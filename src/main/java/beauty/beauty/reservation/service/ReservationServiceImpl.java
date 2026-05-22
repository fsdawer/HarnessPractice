package beauty.beauty.reservation.service;

import beauty.beauty.chat.entity.ChatRoom;
import beauty.beauty.chat.repository.ChatRoomRepository;
import beauty.beauty.chat.service.ChatService;
import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.reservation.dto.CursorResponse;
import beauty.beauty.reservation.dto.ReservationRequest;
import beauty.beauty.reservation.dto.ReservationResponse;
import beauty.beauty.reservation.entity.Reservation;
import beauty.beauty.reservation.entity.ReservationImage;
import beauty.beauty.reservation.repository.ReservationImageRepository;
import beauty.beauty.reservation.repository.ReservationRepository;
import beauty.beauty.stylist.entity.OperatingHours;
import beauty.beauty.stylist.entity.StylistProfile;
import beauty.beauty.stylist.entity.StylistServiceItem;
import beauty.beauty.stylist.repository.OperatingHoursRepository;
import beauty.beauty.stylist.repository.StylistHolidayRepository;
import beauty.beauty.stylist.repository.StylistProfileRepository;
import beauty.beauty.stylist.repository.StylistServiceRepository;
import beauty.beauty.user.entity.User;
import beauty.beauty.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final UserRepository userRepository;
    private final StylistProfileRepository stylistProfileRepository;
    private final StylistServiceRepository serviceRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationImageRepository reservationImageRepository;
    private final OperatingHoursRepository operatingHoursRepository;
    private final StylistHolidayRepository stylistHolidayRepository;
    private final ChatService chatService;
    private final ChatRoomRepository chatRoomRepository;
    // [AFTER] 이벤트 기반 비동기 처리
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final TransactionTemplate transactionTemplate;

    private static final String UPLOAD_DIR = "uploads/reservation-images/";

    // ─────────────────────────────────────────────────────────────────────────
    // 1. 예약 생성  [AFTER — Redis SETNX 분산락 + 비동기 이벤트]
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    @CacheEvict(value = "booked_times",
                key = "#request.stylistId + ':' + #request.reservedAt.toLocalDate().toString()")
    public ReservationResponse createReservation(Long userId, ReservationRequest request) {

        // [Flow 1] 예약 동시성 제어를 위한 Redis 분산락 획득 (Fail-Fast)
        // DB 커넥션을 점유하지 않은 상태(@Transactional 밖)에서 Redis 서버와 먼저 통신하여
        // 락을 획득하지 못하면 즉시 예외를 던지고 종료시킵니다. (커넥션 고갈 방지)
        String lockKey = "lock:reservation:" + request.getStylistId() + ":" + request.getReservedAt();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new CustomException(ErrorCode.ALREADY_RESERVED);
        }

        try {
            // [Flow 2] 트랜잭션 바운더리 진입 (TransactionTemplate 적용)
            // 락을 무사히 획득한 스레드만 트랜잭션을 시작하며,
            // 최대한 짧은 시간 동안만 DB 커넥션을 꺼내 쓰고 즉시 반납합니다.
            return transactionTemplate.execute(status -> {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

                StylistProfile stylist = stylistProfileRepository.findById(request.getStylistId())
                        .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_NOT_FOUND));

                StylistServiceItem stylistServiceItem = serviceRepository.findById(request.getServiceId())
                        .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_NOT_FOUND));

                if (!stylistServiceItem.getStylistProfile().getId().equals(stylist.getId())) {
                    throw new CustomException(ErrorCode.SERVICE_NOT_OWNED);
                }

                LocalDateTime reservedAt = request.getReservedAt();
                int dayOfWeekValue = reservedAt.getDayOfWeek().getValue() - 1;
                LocalTime requestedTime = reservedAt.toLocalTime();

                OperatingHours hours = operatingHoursRepository
                        .findByStylistProfileIdAndDayOfWeek(stylist.getId(), dayOfWeekValue)
                        .orElseThrow(() -> new CustomException(ErrorCode.OPERATING_HOURS_NOT_FOUND));

                if (hours.isClosed()) {
                    throw new CustomException(ErrorCode.HOLIDAY);
                }

                // 특정 날짜 휴무 체크
                if (stylistHolidayRepository.existsByStylistProfileIdAndHolidayDate(
                        stylist.getId(), reservedAt.toLocalDate())) {
                    throw new CustomException(ErrorCode.HOLIDAY);
                }

                if (requestedTime.isBefore(hours.getOpenTime()) || requestedTime.isAfter(hours.getCloseTime())) {
                    throw new CustomException(ErrorCode.OUTSIDE_OPERATING_HOURS);
                }

                boolean isBooked = reservationRepository.existsByStylistProfileIdAndReservedAtAndStatusIn(
                        stylist.getId(),
                        request.getReservedAt(),
                        List.of(Reservation.Status.PENDING, Reservation.Status.CONFIRMED)
                );

                if (isBooked) {
                    throw new CustomException(ErrorCode.ALREADY_RESERVED);
                }

                Reservation reservation = Reservation.builder()
                        .user(user)
                        .stylistProfile(stylist)
                        .service(stylistServiceItem)
                        .reservedAt(request.getReservedAt())
                        .status(Reservation.Status.CONFIRMED)
                        .requestMemo(request.getRequestMemo())
                        .totalPrice(stylistServiceItem.getPrice())
                        .createdAt(LocalDateTime.now())
                        .build();

                // [Flow 3] 예약 정보 DB 저장
                // 여기서 DB INSERT 쿼리가 발생합니다.
                Reservation saved = reservationRepository.save(reservation);
                ChatRoom chatRoom = chatService.createRoomForReservation(saved);

                // [Flow 4] 이벤트 발행 (Redis Streams Publish)
                // DB 커밋 완료 후 Stream 발행 — 롤백 시 메시지가 남는 문제를 방지하기 위해 
                // TransactionSynchronizationManager.afterCommit() 시점에 발송합니다.
                long savedId = saved.getId();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        // 통신 오버헤드를 막기 위해 무거운 객체(Reservation) 대신 reservationId 단일 값만 던집니다.
                        stringRedisTemplate.opsForStream().add(
                                "reservation-events",
                                Map.of("reservationId", String.valueOf(savedId))
                        );
                        log.info("[Reservation] Stream 발행 완료 — reservationId={}", savedId);
                    }
                });

                log.info("[Reservation] 예약 생성 완료 — reservationId={}, stylistId={}, userId={}",
                        saved.getId(), stylist.getId(), userId);

                return ReservationResponse.from(saved, chatRoom.getId());
            });
        } finally {
            // [Flow 5] Redis 락 해제
            // DB 트랜잭션이 성공이든 예외든 완전히 끝난 직후 락을 반납하여 다른 스레드가 진입할 수 있게 합니다.
            redisTemplate.delete(lockKey);
        }
    }

    // 2. 예약 이미지 업로드
    @Override
    @Transactional
    public void uploadImages(Long userId, Long reservationId, List<MultipartFile> files) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_MY_RESERVATION);
        }

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            String savedUrl = saveFile(file);
            reservationImageRepository.save(
                    ReservationImage.builder()
                            .reservation(reservation)
                            .imageUrl(savedUrl)
                            .build()
            );
        }
    }

    private String saveFile(MultipartFile file) {
        try {
            File dir = new File(UPLOAD_DIR);
            if (!dir.exists()) dir.mkdirs();

            String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID() + (ext != null ? "." + ext : "");
            Path path = Paths.get(UPLOAD_DIR + filename);
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/reservation-images/" + filename;
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_SAVE_FAILED);
        }
    }




    // 3. 내 예약 리스트 (마이페이지용) — Offset 방식 (하위 호환 유지)
    @Override
    @Transactional(readOnly = true)
    public Page<ReservationResponse> getMyReservations(Long userId, Pageable pageable) {
        Page<Reservation> page = reservationRepository.findByUserIdWithDetails(userId, pageable);
        Map<Long, Long> chatRoomIdMap = buildChatRoomIdMap(page.getContent());
        return page.map(r -> ReservationResponse.from(r, chatRoomIdMap.get(r.getId())));
    }

    // 3-1. 내 예약 리스트 Cursor 방식 — No-Offset, PK 범위 조건으로 항상 size개만 읽음
    @Override
    @Transactional(readOnly = true)
    public CursorResponse<ReservationResponse> getMyReservationsCursor(Long userId, Long lastId, int size) {
        // size+1개를 가져와서 hasMore 판단 (추가 쿼리 없이)
        List<Reservation> rows = reservationRepository.findByUserIdCursor(
                userId, lastId, PageRequest.of(0, size + 1));

        boolean hasMore = rows.size() > size;
        List<Reservation> content = hasMore ? rows.subList(0, size) : rows;

        Map<Long, Long> chatRoomIdMap = buildChatRoomIdMap(content);
        List<ReservationResponse> responses = content.stream()
                .map(r -> ReservationResponse.from(r, chatRoomIdMap.get(r.getId())))
                .toList();

        Long nextCursorId = content.isEmpty() ? null : content.get(content.size() - 1).getId();
        return CursorResponse.of(responses, hasMore, nextCursorId);
    }



    // 4. 예약 상세 조회
    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(Long userId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));

        boolean isMyReservation = reservation.getUser().getId().equals(userId);
        boolean isMyStylistReservation = reservation.getStylistProfile().getUser().getId().equals(userId);

        if (!isMyReservation && !isMyStylistReservation) {
            throw new CustomException(ErrorCode.NOT_MY_RESERVATION);
        }

        Long chatRoomId = chatRoomRepository.findByReservationId(reservationId)
                .map(ChatRoom::getId).orElse(null);
        return ReservationResponse.from(reservation, chatRoomId);
    }



    // 5. 예약 취소
    // @CacheEvict(allEntries=true): 취소된 슬롯이 다시 비어야 하므로 캐시를 무효화해야 함
    // 그런데 캐시 키(stylistId:날짜)를 만들려면 reservationId로 DB를 먼저 읽어야 함
    // @CacheEvict는 메서드 실행 전에 키를 평가하므로 이 시점에 stylistId를 알 수 없음
    // → 어떤 키를 지워야 할지 모르니 booked_times 캐시 전체를 비움 (취소는 드문 작업이라 허용)
    @Override
    @Transactional
    @CacheEvict(value = "booked_times", allEntries = true)
    public void cancelReservation(Long userId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_MY_RESERVATION);
        }

        if (reservation.getStatus() != Reservation.Status.CONFIRMED
                && reservation.getStatus() != Reservation.Status.PENDING) {
            throw new CustomException(ErrorCode.INVALID_RESERVATION_STATUS);
        }

        reservation.setStatus(Reservation.Status.CANCELLED);
        reservationRepository.save(reservation);

        // 빈자리 알림 이벤트 발행 (Redis Streams)
        long stylistId = reservation.getStylistProfile().getId(); // 예약된 미용사 아이디
        LocalDate cancelDate = reservation.getReservedAt().toLocalDate(); // 예약 날짜
        LocalTime cancelTime = reservation.getReservedAt().toLocalTime(); // 예약 시간

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                stringRedisTemplate.opsForStream().add(
                        "cancel_stream",
                        Map.of(
                                "stylistId", String.valueOf(stylistId),
                                "date", cancelDate.toString(),
                                "time", cancelTime.toString()
                        )
                );
                log.info("[Waiting] 예약 취소 발생, 빈자리 알림 이벤트 발행 — stylistId={}, datetime={}", stylistId, reservation.getReservedAt());
            }
        });
    }



    // 6. 미용사 예약 리스트 (예약 관리용) — Cursor 방식
    @Override
    @Transactional(readOnly = true)
    public CursorResponse<ReservationResponse> getStylistReservations(Long userId, Long lastId, int size) {
        StylistProfile stylistProfile = stylistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_PROFILE_NOT_FOUND));

        List<Reservation> rows = reservationRepository.findByStylistProfileIdCursor(
                stylistProfile.getId(), lastId, PageRequest.of(0, size + 1));

        boolean hasMore = rows.size() > size;
        List<Reservation> content = hasMore ? rows.subList(0, size) : rows;

        Map<Long, Long> chatRoomIdMap = buildChatRoomIdMap(content);
        List<ReservationResponse> responses = content.stream()
                .map(r -> ReservationResponse.from(r, chatRoomIdMap.get(r.getId())))
                .toList();

        Long nextCursorId = content.isEmpty() ? null : content.get(content.size() - 1).getId();
        return CursorResponse.of(responses, hasMore, nextCursorId);
    }


    // 7. 예약 상태 변경 (미용사용)
    @Override
    @Transactional
    public void updateReservationStatus(Long userId, Long reservationId, String status) {
        StylistProfile stylistProfile = stylistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_PROFILE_NOT_FOUND));

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getStylistProfile().getId().equals(stylistProfile.getId())) {
            throw new CustomException(ErrorCode.NOT_MY_RESERVATION);
        }

        Reservation.Status newStatus;
        try {
            newStatus = Reservation.Status.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_RESERVATION_STATUS);
        }

        if (reservation.getStatus() == Reservation.Status.CANCELLED
                || reservation.getStatus() == Reservation.Status.DONE) {
            throw new CustomException(ErrorCode.INVALID_RESERVATION_STATUS);
        }

        if (newStatus == Reservation.Status.DONE) {
            LocalDateTime serviceEnd = reservation.getReservedAt().plusMinutes(reservation.getService().getDuration());
            if(LocalDateTime.now().isBefore(serviceEnd)) {
                throw new CustomException(ErrorCode.INVALID_RESERVATION_STATUS);
            }
        }

        reservation.setStatus(newStatus);
        if (newStatus == Reservation.Status.CONFIRMED) {
            chatService.createRoomForReservation(reservation);
        }
    }



    // 예약 목록 → reservationId:chatRoomId 맵 (N+1 방지용 배치 조회)
    private Map<Long, Long> buildChatRoomIdMap(List<Reservation> reservations) {
        if (reservations.isEmpty()) return Map.of();
        List<Long> ids = reservations.stream().map(Reservation::getId).toList();
        return chatRoomRepository.findByReservationIdIn(ids).stream()
                .collect(Collectors.toMap(cr -> cr.getReservation().getId(), ChatRoom::getId));
    }

    // 8. 특정 날짜 예약된 시간대 조회
    // @Cacheable: 같은 미용사+날짜 조합은 30분간 Redis에서 바로 반환 (DB 조회 없음)
    // 캐시 키 예) "booked_times::1:2026-05-10" → ["10:00","11:00"] 저장
    // createReservation / cancelReservation 이 일어나면 해당 키를 삭제하므로 항상 최신 데이터
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "booked_times", key = "#stylistId + ':' + #date")
    public List<String> getStylistBookedTimes(Long stylistId, String date) {
        LocalDate parsedDate = LocalDate.parse(date);
        LocalDateTime start = parsedDate.atStartOfDay();
        LocalDateTime end = parsedDate.atTime(LocalTime.MAX);

        List<Reservation.Status> validStatuses = List.of(
                Reservation.Status.PENDING, Reservation.Status.CONFIRMED
        );

        return reservationRepository
                .findByStylistProfileIdAndReservedAtBetweenAndStatusIn(stylistId, start, end, validStatuses)
                .stream()
                .map(r -> String.format("%02d:%02d", r.getReservedAt().getHour(), r.getReservedAt().getMinute()))
                .toList();
    }
}
