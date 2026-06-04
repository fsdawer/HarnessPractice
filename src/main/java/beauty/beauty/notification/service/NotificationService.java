package beauty.beauty.notification.service;

import beauty.beauty.notification.dto.NotificationMessage;
import beauty.beauty.notification.entity.NotificationHistory;
import beauty.beauty.notification.repository.NotificationHistoryRepository;
import beauty.beauty.reservation.entity.Reservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SseEmitterService             sseEmitterService;
    private final StringRedisTemplate           stringRedisTemplate;
    private final ObjectMapper                  redisObjectMapper;
    private final NotificationHistoryRepository notificationHistoryRepository;

    private static final String PENDING_KEY_PREFIX = "notifications:";
    private static final Duration PENDING_TTL = Duration.ofDays(7);

    @Async("reservationTaskExecutor")
    public void notifyReservationCreated(Reservation reservation) {
        Long stylistUserId = reservation.getStylistProfile().getUser().getId();
        Long clientUserId  = reservation.getUser().getId();

        NotificationMessage msg = NotificationMessage.builder()
                .type("RESERVATION_CREATED")
                .reservationId(reservation.getId())
                .stylistName(reservation.getStylistProfile().getUser().getName())
                .clientName(reservation.getUser().getName())
                .reservedAt(reservation.getReservedAt().toString())
                .message("새 예약이 확정되었습니다.")
                .dedupKey("RESERVATION_CREATED:" + reservation.getId())
                .build();

        sendAndPersist(stylistUserId, msg);
        sendAndPersist(clientUserId, msg);
    }



    // 취소 알림: 엔티티 대신 primitive 값을 받음
    // — afterCommit() 콜백에서 호출되면 JPA 세션이 닫혀 lazy 연관관계 접근 불가하므로
    //   @Transactional 범위 안에서 미리 추출한 값을 전달받아 처리함
    @Async("reservationTaskExecutor")
    public void notifyReservationCancelled(Long reservationId, Long stylistUserId, Long clientUserId,
                                            String stylistName, String clientName, LocalDateTime reservedAt) {
        NotificationMessage msg = NotificationMessage.builder()
                .type("RESERVATION_CANCELLED")
                .reservationId(reservationId)
                .stylistName(stylistName)
                .clientName(clientName)
                .reservedAt(reservedAt.toString())
                .message("예약이 취소되었습니다.")
                .dedupKey("RESERVATION_CANCELLED:" + reservationId)
                .build();

        sendAndPersist(stylistUserId, msg);
        sendAndPersist(clientUserId, msg);
    }

    @Async("reservationTaskExecutor")
    public void notifyWaitingAvailable(Long userId, LocalDate date, LocalTime time) {
        String dateLabel = date.getMonthValue() + "월 " + date.getDayOfMonth() + "일 " + time;
        NotificationMessage msg = NotificationMessage.builder()
                .type("WAITING_AVAILABLE")
                .reservedAt(date + "T" + time)
                .message(dateLabel + " 빈자리가 생겼습니다!")
                .dedupKey("WAITING_AVAILABLE:" + date + "T" + time)
                .build();
        sendAndPersist(userId, msg);
    }


    @Async("reservationTaskExecutor")
    public void sendReminderAsync(Reservation reservation, String timing) {
        String label = "1D".equals(timing) ? "내일" : "1시간 후";
        NotificationMessage msg = NotificationMessage.builder()
                .type("RESERVATION_REMINDER_" + timing)
                .reservationId(reservation.getId())
                .stylistName(reservation.getStylistProfile().getUser().getName())
                .clientName(reservation.getUser().getName())
                .reservedAt(reservation.getReservedAt().toString())
                .message(label + " 예약이 있습니다.")
                .dedupKey("RESERVATION_REMINDER_" + timing + ":" + reservation.getId())
                .build();
        sendAndPersist(reservation.getUser().getId(), msg);
    }

    // SSE 즉시 전송 성공 시 Redis 저장 생략, 실패(연결 없음 포함) 시에만 보관
    // DB 히스토리는 항상 저장
    private void sendAndPersist(Long userId, NotificationMessage msg) {
        log.info("[Notify] sendAndPersist 시작 - userId: {}, type: {}", userId, msg.getType());

        saveHistory(userId, msg);

        boolean delivered = sseEmitterService.send(userId, msg);
        if (delivered) {
            log.info("[Notify] SSE 전송 완료 - userId: {}, type: {}", userId, msg.getType());
            return;
        }

        log.info("[Notify] SSE 미연결 → Redis 보관 시도 - userId: {}, type: {}", userId, msg.getType());
        try {
            String key  = PENDING_KEY_PREFIX + userId;
            String json = redisObjectMapper.writeValueAsString(msg);
            stringRedisTemplate.opsForList().leftPush(key, json);
            stringRedisTemplate.expire(key, PENDING_TTL);
            log.info("[Notify] Redis 보관 완료 - key: {}", key);
        } catch (Exception e) {
            log.warn("[Notify] Redis 저장 실패 - userId: {}", userId, e);
        }
    }

    private void saveHistory(Long userId, NotificationMessage msg) {
        try {
            notificationHistoryRepository.save(NotificationHistory.builder()
                    .userId(userId)
                    .type(msg.getType())
                    .message(msg.getMessage())
                    .build());
            log.info("[Notify] DB 히스토리 저장 완료 - userId: {}, type: {}", userId, msg.getType());
        } catch (Exception e) {
            log.warn("[Notify] 히스토리 DB 저장 실패 - userId: {}", userId, e);
        }
    }

    // 재연결 시 미전달 알림 원자적 조회·삭제 (RENAME → range → delete)
    public List<NotificationMessage> flushPending(Long userId) {
        String key    = PENDING_KEY_PREFIX + userId;
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) return List.of();
        String tmpKey = key + ":flush";
        stringRedisTemplate.rename(key, tmpKey);
        List<String> raw = stringRedisTemplate.opsForList().range(tmpKey, 0, -1);
        stringRedisTemplate.delete(tmpKey);

        if (raw == null || raw.isEmpty()) return List.of();

        // leftPush로 최신이 앞에 있으므로 reverse → 오래된 순서로 반환 (프론트에서 unshift 시 최신이 위로)
        Collections.reverse(raw);
        return raw.stream()
                .map(json -> {
                    try { return redisObjectMapper.readValue(json, NotificationMessage.class); }
                    catch (Exception e) { return null; }
                })
                .filter(m -> m != null)
                .toList();
    }
}
