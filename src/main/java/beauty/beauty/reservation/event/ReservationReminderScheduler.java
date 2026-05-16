package beauty.beauty.reservation.event;

import beauty.beauty.notification.service.NotificationService;
import beauty.beauty.reservation.entity.Reservation;
import beauty.beauty.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 예약 리마인더 스케줄러 — 매 정각 실행
 *
 * 24시간 전: reservedAt ∈ [now+23:30, now+24:30]
 *  1시간 전: reservedAt ∈ [now+00:30, now+01:30]
 *
 * ±30분 윈도우로 매 1시간마다 딱 한 번 해당 예약을 포착.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationReminderScheduler {

    private final ReservationRepository reservationRepository;
    private final NotificationService   notificationService;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Scheduled(cron = "0 0 * * * *")
    public void sendReminders() {
        LocalDateTime now = LocalDateTime.now();

        // 24시간 전 리마인더
        List<Reservation> dayBefore = reservationRepository.findForReminder(
                now.plusHours(23).plusMinutes(30),
                now.plusHours(24).plusMinutes(30),
                Reservation.Status.CONFIRMED
        );
        dayBefore.forEach(r -> {
            String key = "sent:reminder:" + r.getId() + ":1D";
            if (Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 25, java.util.concurrent.TimeUnit.HOURS))) {
                notificationService.sendReminderAsync(r, "1D");
            }
        });
        if (!dayBefore.isEmpty())
            log.info("[리마인더] 24시간 전 알림 {}건 발송", dayBefore.size());

        // 1시간 전 리마인더
        List<Reservation> hourBefore = reservationRepository.findForReminder(
                now.plusMinutes(30),
                now.plusMinutes(90),
                Reservation.Status.CONFIRMED
        );
        hourBefore.forEach(r -> {
            String key = "sent:reminder:" + r.getId() + ":1H";
            if (Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 2, java.util.concurrent.TimeUnit.HOURS))) {
                notificationService.sendReminderAsync(r, "1H");
            }
        });
        if (!hourBefore.isEmpty())
            log.info("[리마인더] 1시간 전 알림 {}건 발송", hourBefore.size());
    }
}
