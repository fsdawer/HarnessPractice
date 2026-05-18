package beauty.beauty.payment.service;

import beauty.beauty.chat.service.ChatService;
import beauty.beauty.coupon.entity.UserCoupon;
import beauty.beauty.coupon.repository.UserCouponRepository;
import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.payment.dto.*;
import beauty.beauty.payment.entity.Payment;
import beauty.beauty.payment.repository.PaymentRepository;
import beauty.beauty.reservation.entity.Reservation;
import beauty.beauty.reservation.repository.ReservationRepository;
import beauty.beauty.user.entity.User;
import beauty.beauty.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final UserCouponRepository userCouponRepository;
    private final ChatService chatService;
    private final TransactionTemplate transactionTemplate; // 외부 API와 트랜잭션 분리용

    @Value("${toss.secret-key}")
    private String secretKey;

    // HttpClient는 스레드 안전하므로 재사용 (매 API 호출마다 새로 생성하면 성능 낭비)
    private final HttpClient httpClient = HttpClient.newHttpClient();


    // 결제 준비 — orderId 발급 + Payment(PENDING) DB 저장
    @Override
    @Transactional
    public PaymentPrepareResponse prepare(Long userId, PaymentPrepareRequest request) {

        // [Flow 1] 결제 권한 및 예약 유효성 검증
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Reservation reservation = reservationRepository.findById(request.getReservationId())
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));

        // 해당 예약이 내 예약인지 검증 (유저 조회에서 조회된 아이디가 예약 테이블 안에있는 아이디와 같은지)
        if(!reservation.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_MY_RESERVATION);
        }

        // 예약 ID로 기존 결제 내역이 있는지 조회
        // 파라미터는 예약 테이블에 저장된 예약 아이디를 가져와야하기 때문에 reservation.getId
        // request에 있는 예약 ID는 요청에 포함된 ID이기 때문임
        paymentRepository.findByReservationId(reservation.getId())
                .ifPresent(existingPayment -> {
                            // 이미 결제 완료(PAID) 상태라면 예외 발생
                            if (existingPayment.getStatus() == Payment.PayStatus.PAID) {
                                throw new CustomException(ErrorCode.PAYMENT_ALREADY_PAID);
                            }

                            // 환불된 건에 대해서도 재결제를 막고 싶다면 추가
                            if(existingPayment.getStatus() == Payment.PayStatus.REFUNDED) {
                                throw new CustomException(ErrorCode.PAYMENT_ALREADY_REFUNDED);
                            }

                    // 결제창을 열었다가 닫아서 PENDING 상태로 남아있는 경우, 이전 내역은 삭제
                    if (existingPayment.getStatus() == Payment.PayStatus.PENDING) {
                        paymentRepository.delete(existingPayment);
                        paymentRepository.flush(); // 즉시 쿼리를 날려서 새 Payment 저장 시 유니크 제약조건(예약 1개당 Payment 1개) 충돌을 방지합니다.
                    }
                });

        // [Flow 2] 쿠폰 처리 (선택)
        int originalAmount = reservation.getTotalPrice();
        int discountAmount = 0;
        UserCoupon appliedCoupon = null;

        if (request.getUserCouponId() != null) {
            appliedCoupon = userCouponRepository.findValidOne(request.getUserCouponId(), userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.COUPON_INVALID));

            var coupon = appliedCoupon.getCoupon();
            if (originalAmount < coupon.getMinPrice()) {
                throw new CustomException(ErrorCode.COUPON_MIN_PRICE_NOT_MET);
            }
            discountAmount = originalAmount * coupon.getDiscountRate() / 100;
            if (coupon.getMaxDiscount() != null) {
                discountAmount = Math.min(discountAmount, coupon.getMaxDiscount());
            }
            appliedCoupon.use();
        }

        int finalAmount = Math.max(0, originalAmount - discountAmount);

        // [Flow 3] 고유 주문번호(orderId) 생성 및 결제 엔티티 생성
        UUID uuid = UUID.randomUUID();
        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        String orderId = Base64.getUrlEncoder().withoutPadding().encodeToString(bb.array());

        Payment payment = Payment.builder()
                .reservation(reservation)
                .orderId(orderId)
                .amount(finalAmount)
                .discountAmount(discountAmount)
                .userCoupon(appliedCoupon)
                .status(Payment.PayStatus.PENDING)
                .method(Payment.Method.TOSS)
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        return PaymentPrepareResponse.builder()
                .paymentId(savedPayment.getId())
                .orderId(savedPayment.getOrderId())
                .amount(savedPayment.getAmount())
                .originalAmount(originalAmount)
                .discountAmount(discountAmount)
                .build();
    }


    // 결제 승인 — 토스페이먼츠 서버에 최종 승인 요청 후 PAID 처리
    // [트랜잭션 분리]: 외부 API 호출 시 DB 커넥션 고갈 방지
    @Override
    public PaymentResponse confirm(Long userId, PaymentConfirmRequest request) {
        
        // [Flow 1] 사전 검증 (DB 트랜잭션 O)
        // 금액 조작 방지를 위해, DB에 저장된 금액과 클라이언트가 보낸 금액이 일치하는지 트랜잭션 내에서 검증합니다.
        Payment validatedPayment = transactionTemplate.execute(status -> {
            Payment payment = paymentRepository.findByOrderId(request.getOrderId())
                    .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));

            if (!payment.getReservation().getUser().getId().equals(userId)) {
                throw new CustomException(ErrorCode.NOT_MY_PAYMENT);
            }

            if (payment.getStatus() == Payment.PayStatus.PAID) {
                throw new CustomException(ErrorCode.PAYMENT_ALREADY_PAID);
            }

            if (payment.getAmount() != request.getAmount()) {
                throw new CustomException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
            }
            return payment;
        });

        // [Flow 2] 토스페이먼츠 최종 승인 요청 (외부 API 통신, DB 트랜잭션 X)
        // mock_ 접두사 paymentKey는 개발 환경 테스트용으로 Toss API 호출을 건너뜁니다.
        if (!request.getPaymentKey().startsWith("mock_")) {
            try {
                String credentials = Base64.getEncoder()
                        .encodeToString((secretKey.trim() + ":").getBytes(StandardCharsets.UTF_8));
                String body = String.format(
                        "{\"paymentKey\":\"%s\",\"orderId\":\"%s\",\"amount\":%d}",
                        request.getPaymentKey(), request.getOrderId(), request.getAmount()
                );

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.tosspayments.com/v1/payments/confirm"))
                        .header("Authorization", "Basic " + credentials)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    log.error("토스페이먼츠 승인 실패 [{}]: {}", response.statusCode(), response.body());
                    throw new CustomException(ErrorCode.TOSS_API_FAILED);
                }
            } catch (IOException | InterruptedException e) {
                log.error("토스페이먼츠 통신 중 오류 발생", e);
                throw new CustomException(ErrorCode.TOSS_API_FAILED);
            }
        } else {
            log.info("[개발 모드] mock paymentKey 감지 — Toss API 호출 생략: {}", request.getPaymentKey());
        }

        // [Flow 3] DB 상태 업데이트 (DB 트랜잭션 O)
        // 토스 서버 결제가 성공적으로 완료되었으므로, 결제(PAID) 및 예약(CONFIRMED) 상태를 최종 반영합니다.
        return transactionTemplate.execute(status -> {
            Payment paymentToUpdate = paymentRepository.findById(validatedPayment.getId()).orElseThrow();
            
            paymentToUpdate.setStatus(Payment.PayStatus.PAID);
            paymentToUpdate.setPaymentKey(request.getPaymentKey());
            paymentToUpdate.setPaidAt(LocalDateTime.now());

            Reservation reservation = paymentToUpdate.getReservation();
            reservation.setStatus(Reservation.Status.CONFIRMED);
            reservationRepository.save(reservation);

            // [Flow 4] 결제 확정 즉시 1:1 채팅방 생성
            chatService.createRoomForReservation(reservation);

            return PaymentResponse.from(paymentToUpdate);
        });
    }


    // 환불 — 토스페이먼츠 서버에 취소 요청 후 REFUNDED 처리
    // [트랜잭션 분리]: 외부 API 호출 시 DB 커넥션 고갈 방지
    @Override
    public void refund(Long userId, Long paymentId, RefundRequest request) {
        
        // 1. [DB 트랜잭션 O] 환불 사전 검증
        Payment validatedPayment = transactionTemplate.execute(status -> {
            Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));

            if (!payment.getReservation().getUser().getId().equals(userId)) {
                throw new CustomException(ErrorCode.NOT_MY_PAYMENT);
            }

            if (payment.getStatus() != Payment.PayStatus.PAID) {
                throw new CustomException(ErrorCode.REFUND_NOT_ALLOWED);
            }
            return payment;
        });

        // 2. [DB 트랜잭션 X] 토스페이먼츠 취소 요청
        try {
            String credentials = Base64.getEncoder()
                    .encodeToString((secretKey.trim() + ":").getBytes(StandardCharsets.UTF_8));

            String body = String.format("{\"cancelReason\":\"%s\"}", request.getCancelReason());

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.tosspayments.com/v1/payments/" + validatedPayment.getPaymentKey() + "/cancel"))
                    .header("Authorization", "Basic " + credentials)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("토스페이먼츠 환불 실패 [{}]: {}", response.statusCode(), response.body());
                throw new CustomException(ErrorCode.TOSS_API_FAILED);
            }
        } catch (IOException | InterruptedException e) {
            log.error("토스페이먼츠 환불 통신 중 오류 발생", e);
            throw new CustomException(ErrorCode.TOSS_API_FAILED);
        }
        
        // 3. [DB 트랜잭션 O] 상태 업데이트 + 쿠폰 복원
        transactionTemplate.execute(status -> {
            Payment paymentToUpdate = paymentRepository.findById(validatedPayment.getId()).orElseThrow();
            paymentToUpdate.setStatus(Payment.PayStatus.REFUNDED);
            if (paymentToUpdate.getUserCoupon() != null) {
                paymentToUpdate.getUserCoupon().restore();
            }
            return null;
        });
    }


    // 결제 실패/취소 시 orderId로 PENDING 결제 + 연관 예약 즉시 취소
    @Override
    @Transactional
    public void cancelPendingByOrderId(String orderId) {
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            if (payment.getStatus() != Payment.PayStatus.PENDING) return;
            // 결제 상태가 pending이 아니면 메서드 종료 아무것도 없이 리턴

            if (payment.getUserCoupon() != null) {
                payment.getUserCoupon().restore();
            }
            Reservation reservation = payment.getReservation();
            if (reservation != null && reservation.getStatus() == Reservation.Status.PENDING) {
                reservation.setStatus(Reservation.Status.CANCELLED);
                reservationRepository.save(reservation);
            }
            paymentRepository.delete(payment);
            log.info("결제 실패로 인한 즉시 취소 처리 — orderId: {}", orderId);
        });
    }


    // 내 결제 내역 조회
    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getMyPayments(Long userId) {
        // 1. userId로 본인 예약 ID 목록 조회
        List<Long> reservationIds = reservationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(Reservation::getId)
                .toList();

        // 2. 예약 ID 목록으로 결제 내역 조회
        List<Payment> payments = paymentRepository.findByReservationIdInOrderByCreatedAtAsc(reservationIds);

        // 3. PaymentResponse로 변환 후 반환
        return payments.stream()
                .map(PaymentResponse::from)
                .toList();
    }

    // orderId로 단건 결제 조회 (결제 성공 화면용)
    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getByOrderId(Long userId, String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
        // 본인 결제인지 검증
        if (!payment.getReservation().getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_MY_PAYMENT);
        }
        return PaymentResponse.from(payment);
    }
}
