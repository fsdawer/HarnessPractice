package beauty.beauty.payment.event;

import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.payment.dto.RefundRequest;
import beauty.beauty.payment.entity.Payment;
import beauty.beauty.payment.repository.PaymentRepository;
import beauty.beauty.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * [단위 테스트] PaymentCancelStreamListener.onMessage()
 *
 * 예약 취소 이벤트를 소비하여 자동 환불을 처리하는 Stream 리스너의
 * 분기 로직(결제 없음 / PAID 아닌 상태 / 정상 환불 / 이미 환불 / Toss 실패)을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentCancelStreamListenerTest {

    private static final String STREAM_KEY = "reservation-cancel-refund-stream";
    private static final String CONSUMER_GROUP = "refund-group";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    @InjectMocks
    private PaymentCancelStreamListener listener;

    @BeforeEach
    void setUp() {
        // ack() 내부에서 stringRedisTemplate.opsForStream()을 호출하므로 lenient stub
        // (Toss 실패 케이스처럼 ack()를 타지 않는 경로에서도 UnnecessaryStubbingException 방지)
        lenient().when(stringRedisTemplate.opsForStream()).thenReturn(streamOperations);
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────────

    private MapRecord<String, String, String> buildMessage(long reservationId, long userId) {
        return StreamRecords.mapBacked(Map.of(
                "reservationId", String.valueOf(reservationId),
                "userId", String.valueOf(userId)
        ))
                .withStreamKey(STREAM_KEY)
                .withId(RecordId.of("0-1"));
    }

    private Payment buildPayment(long paymentId, Payment.PayStatus status) {
        return Payment.builder()
                .id(paymentId)
                .amount(50000)
                .method(Payment.Method.TOSS)
                .status(status)
                .build();
    }

    // ── 테스트 1: 결제 없는 취소 ─────────────────────────────────────────────

    @Test
    @DisplayName("결제 없는 취소 시 ACK를 보내고 refund()를 호출하지 않는다")
    void onMessage_noPayment_ackWithoutRefund() {
        // given
        long reservationId = 1L;
        MapRecord<String, String, String> message = buildMessage(reservationId, 10L);
        when(paymentRepository.findByReservationId(reservationId)).thenReturn(Optional.empty());

        // when
        listener.onMessage(message);

        // then
        verify(paymentService, never()).refund(anyLong(), anyLong(), any(RefundRequest.class));
        verify(streamOperations, times(1))
                .acknowledge(eq(STREAM_KEY), eq(CONSUMER_GROUP), eq(message.getId()));
    }

    // ── 테스트 2: PAID 아닌 상태 ─────────────────────────────────────────────

    @Test
    @DisplayName("결제 상태가 PENDING이면 ACK를 보내고 refund()를 호출하지 않는다")
    void onMessage_pendingStatus_ackWithoutRefund() {
        // given
        long reservationId = 2L;
        MapRecord<String, String, String> message = buildMessage(reservationId, 10L);
        Payment payment = buildPayment(20L, Payment.PayStatus.PENDING);
        when(paymentRepository.findByReservationId(reservationId)).thenReturn(Optional.of(payment));

        // when
        listener.onMessage(message);

        // then
        verify(paymentService, never()).refund(anyLong(), anyLong(), any(RefundRequest.class));
        verify(streamOperations, times(1))
                .acknowledge(eq(STREAM_KEY), eq(CONSUMER_GROUP), eq(message.getId()));
    }

    // ── 테스트 3: 정상 환불 ──────────────────────────────────────────────────

    @Test
    @DisplayName("결제 상태가 PAID이면 refund()를 호출하고 ACK를 보낸다")
    void onMessage_paidStatus_refundAndAck() {
        // given
        long reservationId = 3L;
        long userId = 10L;
        long paymentId = 30L;
        MapRecord<String, String, String> message = buildMessage(reservationId, userId);
        Payment payment = buildPayment(paymentId, Payment.PayStatus.PAID);
        when(paymentRepository.findByReservationId(reservationId)).thenReturn(Optional.of(payment));

        // when
        listener.onMessage(message);

        // then
        verify(paymentService, times(1)).refund(eq(userId), eq(paymentId), any(RefundRequest.class));
        verify(streamOperations, times(1))
                .acknowledge(eq(STREAM_KEY), eq(CONSUMER_GROUP), eq(message.getId()));
    }

    // ── 테스트 4: 이미 환불된 경우 (멱등) ────────────────────────────────────

    @Test
    @DisplayName("refund()가 REFUND_NOT_ALLOWED를 던지면 ACK를 보내고 예외를 전파하지 않는다")
    void onMessage_refundNotAllowed_ackIdempotent() {
        // given
        long reservationId = 4L;
        long userId = 10L;
        long paymentId = 40L;
        MapRecord<String, String, String> message = buildMessage(reservationId, userId);
        Payment payment = buildPayment(paymentId, Payment.PayStatus.PAID);
        when(paymentRepository.findByReservationId(reservationId)).thenReturn(Optional.of(payment));
        doThrow(new CustomException(ErrorCode.REFUND_NOT_ALLOWED))
                .when(paymentService).refund(anyLong(), anyLong(), any(RefundRequest.class));

        // when
        listener.onMessage(message);

        // then
        verify(streamOperations, times(1))
                .acknowledge(eq(STREAM_KEY), eq(CONSUMER_GROUP), eq(message.getId()));
    }

    // ── 테스트 5: Toss API 실패 ──────────────────────────────────────────────

    @Test
    @DisplayName("refund()가 TOSS_API_FAILED를 던지면 ACK를 보내지 않아 Pending 상태로 유지된다")
    void onMessage_tossApiFailed_noAck() {
        // given
        long reservationId = 5L;
        long userId = 10L;
        long paymentId = 50L;
        MapRecord<String, String, String> message = buildMessage(reservationId, userId);
        Payment payment = buildPayment(paymentId, Payment.PayStatus.PAID);
        when(paymentRepository.findByReservationId(reservationId)).thenReturn(Optional.of(payment));
        doThrow(new CustomException(ErrorCode.TOSS_API_FAILED))
                .when(paymentService).refund(anyLong(), anyLong(), any(RefundRequest.class));

        // when
        listener.onMessage(message);

        // then
        verify(streamOperations, never())
                .acknowledge(any(), any(), any(RecordId.class));
    }
}
