package beauty.beauty.payment.service;

import beauty.beauty.payment.entity.Payment;
import beauty.beauty.payment.repository.PaymentRepository;
import beauty.beauty.reservation.entity.Reservation;
import beauty.beauty.reservation.repository.ReservationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Slf4j
@Component
public class PaymentCleanupScheduler {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${toss.secret-key}")
    private String secretKey;

    public PaymentCleanupScheduler(
            PaymentRepository paymentRepository,
            ReservationRepository reservationRepository,
            TransactionTemplate transactionTemplate,
            @Qualifier("redisObjectMapper") ObjectMapper objectMapper) {
        this.paymentRepository     = paymentRepository;
        this.reservationRepository = reservationRepository;
        this.transactionTemplate   = transactionTemplate;
        this.objectMapper          = objectMapper;
    }

    @Scheduled(fixedDelay = 60000)
    public void cleanupExpiredPayments() {
        // 현재 시각 기준 10분 전 시점 계산 — 이 시각 이전에 생성된 PENDING이 만료 대상
        LocalDateTime expiredTime = LocalDateTime.now().minusMinutes(10);

        // 10분이 지나도 PENDING 상태인 결제 목록 조회
        List<Payment> expiredPayments = paymentRepository
                .findByStatusAndCreatedAtBefore(Payment.PayStatus.PENDING, expiredTime);
        if (expiredPayments.isEmpty()) return;

        for (Payment payment : expiredPayments) {
            try {
                processExpiredPayment(payment);
            } catch (Exception e) {
                // 한 건 실패해도 나머지는 계속 처리
                // 실패한 건은 DB에서 삭제되지 않으므로 다음 스케줄러 실행(1분 후)에서 자동 재시도
                log.error("[Cleanup] 처리 실패, 다음 실행에서 재시도 — paymentId={}", payment.getId(), e);
            }
        }
    }

    private void processExpiredPayment(Payment payment) throws Exception {
        // Toss에 실제 결제 상태 조회 — 결제창 진입 전 만료된 건은 Toss에 기록 자체가 없어 404 반환 → null
        String tossResponse = queryTossOrder(payment.getOrderId());

        if (tossResponse != null) {
            // Toss 응답 JSON에서 status(결제 상태)와 paymentKey(결제 키) 추출
            JsonNode node = objectMapper.readTree(tossResponse);
            String tossStatus = node.path("status").asText("");
            String paymentKey = node.path("paymentKey").asText(null);

            // Toss는 DONE(결제 완료)인데 DB는 PENDING → confirm() Flow 3 실패로 인한 데이터 불일치
            // → 보상 트랜잭션: Toss 취소 API 호출로 실제 환불 처리
            if ("DONE".equals(tossStatus) && paymentKey != null) {
                cancelTossPayment(paymentKey);
                log.warn("[보상 트랜잭션] DB 불일치 건 Toss 취소 완료 — orderId={}", payment.getOrderId());
            }
        }

        // DB 정리 — 건별 독립 트랜잭션 사용
        // 한 건의 DB 정리 실패가 다른 건의 롤백으로 이어지지 않도록 분리
        // payment는 이 람다 밖에서 로드된 detached 엔티티 → 현재 세션에서 재조회해야 lazy 프록시 사용 가능
        Long paymentId = payment.getId();
        transactionTemplate.execute(status -> {
            Payment p = paymentRepository.findById(paymentId).orElse(null);
            if (p == null) return null;

            Reservation reservation = p.getReservation();

            // 연관 예약이 아직 PENDING이면 CANCELLED로 변경 (슬롯 점유 해제)
            if (reservation != null && reservation.getStatus() == Reservation.Status.PENDING) {
                reservation.setStatus(Reservation.Status.CANCELLED);
                reservationRepository.save(reservation);
            }

            // 만료된 결제 레코드 삭제
            paymentRepository.delete(p);
            return null;
        });
        log.info("[Cleanup] 만료 결제 정리 완료 — paymentId={}", payment.getId());
    }

    private String queryTossOrder(String orderId) throws Exception {
        // Toss 주문 조회 API 요청 생성 — orderId로 실제 결제 상태 확인
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.tosspayments.com/v1/payments/orders/" + orderId))
                .header("Authorization", "Basic " + tossCredentials())
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // 404 → Toss에 해당 주문 기록 없음 (결제창 열기 전 만료된 정상 케이스) → null 반환
        if (response.statusCode() == 404) return null;

        // 그 외 오류 → 예외 던짐 → cleanupExpiredPayments의 catch에서 로그 후 다음 실행에서 재시도
        if (response.statusCode() != 200) {
            throw new RuntimeException("Toss 조회 실패 [" + response.statusCode() + "]: " + response.body());
        }
        return response.body();
    }

    private void cancelTossPayment(String paymentKey) throws Exception {
        // Toss 결제 취소 API 요청 생성 — paymentKey로 해당 결제 환불
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.tosspayments.com/v1/payments/" + paymentKey + "/cancel"))
                .header("Authorization", "Basic " + tossCredentials())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"cancelReason\":\"시스템 오류로 인한 자동 취소\"}"))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // 취소 실패 시 예외 던짐 → processExpiredPayment의 상위 catch로 전파 → 다음 실행에서 재시도
        if (response.statusCode() != 200) {
            throw new RuntimeException("Toss 취소 실패 [" + response.statusCode() + "]: " + response.body());
        }
    }

    private String tossCredentials() {
        // Toss API는 Basic 인증 사용 — "secretKey:" 문자열을 Base64로 인코딩해 Authorization 헤더에 담음
        return Base64.getEncoder()
                .encodeToString((secretKey.trim() + ":").getBytes(StandardCharsets.UTF_8));
    }
}
