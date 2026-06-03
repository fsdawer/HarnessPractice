package beauty.beauty.payment.event;

import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.payment.dto.RefundRequest;
import beauty.beauty.payment.entity.Payment;
import beauty.beauty.payment.repository.PaymentRepository;
import beauty.beauty.payment.service.PaymentService;
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

import java.util.Map;

/**
 * [Flow] 예약 취소 → 자동 환불 Stream 컨슈머
 *
 * ReservationServiceImpl.cancelReservation()의 afterCommit()에서
 * reservation-cancel-refund-stream에 이벤트를 발행하면 이 리스너가 수신하여
 * PaymentService.refund()를 호출합니다.
 *
 * - 결제 없는 취소: ACK 후 skip
 * - PAID 이외 상태: ACK 후 skip (멱등)
 * - REFUND_NOT_ALLOWED: ACK 후 skip (이미 환불됨)
 * - 그 외 예외: ACK 미전송 → Pending 유지 (재처리 대기)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCancelStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private static final String STREAM_KEY = "reservation-cancel-refund-stream";
    private static final String CONSUMER_GROUP = "refund-group";
    private static final String CONSUMER_NAME = "refund-consumer-1";

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final StringRedisTemplate stringRedisTemplate;
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;

    private Subscription subscription;

    @PostConstruct
    public void init() {
        // [입력] Consumer Group 구독 설정 (Stream/Group 초기화는 RedisStreamConfig에서 일원화)
        this.subscription = listenerContainer.receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                this
        );
        if (!listenerContainer.isRunning()) {
            listenerContainer.start();
        }
        log.info("[PaymentCancel Stream] 구독 시작 — stream={}, group={}", STREAM_KEY, CONSUMER_GROUP);
    }

    @PreDestroy
    public void destroy() {
        if (subscription != null) {
            subscription.cancel();
        }
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        Map<String, String> value = message.getValue();
        String reservationIdStr = value.get("reservationId");
        String userIdStr = value.get("userId");

        log.info("[PaymentCancel Consumer] 메시지 수신 — reservationId={}, userId={}", reservationIdStr, userIdStr);

        try {
            long reservationId = Long.parseLong(reservationIdStr);
            long userId = Long.parseLong(userIdStr);

            // [Flow 1] 결제 조회 — 결제 없는 취소는 ACK 후 skip
            Payment payment = paymentRepository.findByReservationId(reservationId).orElse(null);
            if (payment == null) {
                log.info("[PaymentCancel Consumer] 결제 없는 취소 — reservationId={}, ACK 후 skip", reservationId);
                ack(message);
                return;
            }

            // [Flow 2] PAID 상태가 아니면 ACK 후 skip (멱등)
            if (payment.getStatus() != Payment.PayStatus.PAID) {
                log.info("[PaymentCancel Consumer] 환불 불필요 상태({}) — reservationId={}, ACK 후 skip",
                        payment.getStatus(), reservationId);
                ack(message);
                return;
            }

            // [Flow 3] 환불 처리
            RefundRequest refundRequest = new RefundRequest();
            refundRequest.setCancelReason("예약 취소");
            paymentService.refund(userId, payment.getId(), refundRequest);

            // [Flow 4] 성공 시 ACK
            ack(message);
            log.info("[PaymentCancel Consumer] 환불 완료 ACK — reservationId={}, paymentId={}", reservationId, payment.getId());

        } catch (CustomException e) {
            if (e.getErrorCode() == ErrorCode.REFUND_NOT_ALLOWED) {
                // 이미 환불된 건 — 멱등 처리, ACK 후 skip
                log.info("[PaymentCancel Consumer] 이미 환불된 결제 — reservationId={}, ACK 후 skip", reservationIdStr);
                ack(message);
            } else {
                // 그 외 예외 — ACK 미전송, Pending 유지
                log.error("[PaymentCancel Consumer] 환불 처리 실패 — reservationId={}, error={}", reservationIdStr, e.getMessage());
            }
        } catch (Exception e) {
            // ACK 미전송 → Pending 유지 (재처리 대기)
            log.error("[PaymentCancel Consumer] 예상치 못한 오류 — reservationId={}", reservationIdStr, e);
        }
    }

    private void ack(MapRecord<String, String, String> message) {
        stringRedisTemplate.opsForStream().acknowledge(STREAM_KEY, CONSUMER_GROUP, message.getId());
    }
}
