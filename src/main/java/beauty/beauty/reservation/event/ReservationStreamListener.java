package beauty.beauty.reservation.event;

import beauty.beauty.global.config.RedisStreamConfig;
import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.notification.service.NotificationService;
import beauty.beauty.ranking.service.RankingService;
import beauty.beauty.reservation.entity.Reservation;
import beauty.beauty.reservation.repository.ReservationRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * [After 3] Redis Streams 이벤트 컨슈머
 *
 * 기존 ReservationEventListener(@Async)를 대체합니다.
 * Redis Stream으로부터 reservationId를 받아와 예약을 조회하고,
 * 랭킹 업데이트와 알림 전송을 안정적으로 처리합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final ReservationRepository reservationRepository;
    private final RankingService rankingService;
    private final NotificationService notificationService;
    private final StringRedisTemplate stringRedisTemplate;
    
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;
    private Subscription subscription;

    @PostConstruct
    public void init() {
        // Consumer Group 구독 설정
        this.subscription = listenerContainer.receive(
                Consumer.from(RedisStreamConfig.CONSUMER_GROUP, RedisStreamConfig.CONSUMER_NAME),
                StreamOffset.create(RedisStreamConfig.STREAM_KEY, ReadOffset.lastConsumed()),
                this
        );
        listenerContainer.start();
        log.info("[Redis Stream] ReservationStreamListener 구독 시작");
    }

    @PreDestroy
    public void destroy() {
        if (subscription != null) {
            subscription.cancel();
        }
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String messageId = message.getId().getValue();
        String reservationIdStr = message.getValue().get("reservationId");

        log.info("[Redis Stream] 메시지 수신 - messageId: {}, reservationId: {}", messageId, reservationIdStr);

        try {
            // [Flow 1] Stream에서 수신한 ID로 DB에서 최신 예약 상태를 조회합니다. (정합성 보장)
            // findByIdWithNotificationData: user/stylistProfile.user를 즉시 로딩 —
            // @Transactional이 raw bean에 미적용되어 findById 후 엔티티가 detach되고,
            // @Async 스레드에서 Lazy 연관관계 접근 시 LazyInitializationException 발생 방지
            Long reservationId = Long.parseLong(reservationIdStr);
            Reservation reservation = reservationRepository.findByIdWithNotificationData(reservationId)
                    .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));

            // [Flow 2] 타 도메인 로직 호출 - 1 (랭킹 갱신)
            try {
                rankingService.recalculateScore(reservation.getStylistProfile().getId());
            } catch (Exception e) {
                log.error("[Redis Stream] 랭킹 재계산 실패 - reservationId: {}", reservationId, e);
            }

            // [Flow 3] 타 도메인 로직 호출 - 2 (알림 발송)
            try {
                notificationService.notifyReservationCreated(reservation);
            } catch (Exception e) {
                log.error("[Redis Stream] 알림 전송 실패 - reservationId: {}", reservationId, e);
            }

            // [Flow 4] 성공 도장 (Acknowledge) 전송
            // 모든 비즈니스 로직(랭킹, 알림) 처리가 완료되었을 때만 Redis로 ACK를 보냅니다.
            // ACK가 없으면 Redis는 이 메시지가 처리되지 않았다고 판단하여 보관합니다. (Guaranteed Delivery)
            stringRedisTemplate.opsForStream().acknowledge(RedisStreamConfig.CONSUMER_GROUP, message);
            log.info("[Redis Stream] 메시지 처리 완료 (ACK) - messageId: {}", messageId);

        } catch (Exception e) {
            // [Flow 5] 예외 발생 (에러 처리)
            // ACK를 보내지 않고 종료합니다. 메시지는 Redis Stream의 Pending List에 남아있으므로
            // 나중에 스케줄러 등을 통해 재처리할 수 있습니다. (장애 복구 가능)
            log.error("[Redis Stream] 메시지 처리 중 치명적 오류 - messageId: {}", messageId, e);
        }
    }
}
