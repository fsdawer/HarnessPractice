package beauty.beauty.notification.service;

import beauty.beauty.notification.dto.NotificationMessage;
import beauty.beauty.reservation.entity.Reservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SseEmitterService    sseEmitterService;
    private final StringRedisTemplate  stringRedisTemplate;
    private final ObjectMapper         redisObjectMapper;

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
                .build();
        sendAndPersist(reservation.getUser().getId(), msg);
    }

    // SSE 즉시 전송 + Redis List 보관 (연결 해제 시 재연결 후 수신)
    private void sendAndPersist(Long userId, NotificationMessage msg) {
        sseEmitterService.send(userId, msg);

        try {
            String key  = PENDING_KEY_PREFIX + userId;
            String json = redisObjectMapper.writeValueAsString(msg);
            stringRedisTemplate.opsForList().leftPush(key, json);
            stringRedisTemplate.expire(key, PENDING_TTL);
        } catch (Exception e) {
            log.warn("알림 Redis 저장 실패 - userId: {}", userId, e);
        }
    }

    // 재연결 시 미전달 알림 원자적 조회·삭제 (RENAME → range → delete)
    public List<NotificationMessage> flushPending(Long userId) {
        String key    = PENDING_KEY_PREFIX + userId;
        String tmpKey = key + ":flush";
        stringRedisTemplate.rename(key, tmpKey);
        List<String> raw = stringRedisTemplate.opsForList().range(tmpKey, 0, -1);
        stringRedisTemplate.delete(tmpKey);

        if (raw == null || raw.isEmpty()) return List.of();

        // leftPush로 최신이 앞에 있으므로 reverse → 오래된 순서로 반환 (프론트에서 unshift 시 최신이 위로)
        java.util.Collections.reverse(raw);
        return raw.stream()
                .map(json -> {
                    try { return redisObjectMapper.readValue(json, NotificationMessage.class); }
                    catch (Exception e) { return null; }
                })
                .filter(m -> m != null)
                .toList();
    }
}
