package beauty.beauty.notification.service;

import beauty.beauty.global.config.RedisStreamConfig;
import beauty.beauty.notification.dto.NotificationMessage;
import beauty.beauty.notification.entity.NotificationHistory;
import beauty.beauty.notification.repository.NotificationHistoryRepository;
import beauty.beauty.reservation.entity.Reservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final StringRedisTemplate           stringRedisTemplate;
    private final ObjectMapper                  redisObjectMapper;
    private final NotificationHistoryRepository notificationHistoryRepository;

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

    private void sendAndPersist(Long userId, NotificationMessage msg) {
        saveHistory(userId, msg);

        try {
            String payload  = redisObjectMapper.writeValueAsString(msg);
            String dedupKey = msg.getDedupKey() != null ? msg.getDedupKey() : "";
            stringRedisTemplate.opsForStream().add(
                    StreamRecords.newRecord()
                            .in(RedisStreamConfig.NOTIFY_STREAM_KEY)
                            .ofMap(Map.of(
                                    "userId",   userId.toString(),
                                    "type",     msg.getType(),
                                    "payload",  payload,
                                    "dedupKey", dedupKey
                            ))
            );
            stringRedisTemplate.opsForStream().trim(RedisStreamConfig.NOTIFY_STREAM_KEY, 10000L, true);
            log.info("[Notify] Stream XADD 완료 - userId: {}, type: {}", userId, msg.getType());
        } catch (Exception e) {
            log.warn("[Notify] Stream XADD 실패 - userId: {}, type: {}", userId, msg.getType(), e);
        }
    }

    private void saveHistory(Long userId, NotificationMessage msg) {
        try {
            notificationHistoryRepository.save(NotificationHistory.builder()
                    .userId(userId)
                    .type(msg.getType())
                    .message(msg.getMessage())
                    .build());
        } catch (Exception e) {
            log.warn("[Notify] 히스토리 DB 저장 실패 - userId: {}", userId, e);
        }
    }
}
