package beauty.beauty.stylist.service;

import beauty.beauty.payment.entity.Payment;
import beauty.beauty.payment.repository.PaymentRepository;
import beauty.beauty.reservation.entity.Reservation;
import beauty.beauty.reservation.repository.ReservationRepository;
import beauty.beauty.review.repository.ReviewRepository;
import beauty.beauty.stylist.dto.StylistDashboardResponse;
import beauty.beauty.stylist.entity.StylistProfile;
import beauty.beauty.stylist.entity.StylistServiceItem;
import beauty.beauty.stylist.repository.OperatingHoursRepository;
import beauty.beauty.stylist.repository.SalonRepository;
import beauty.beauty.stylist.repository.StylistProfileRepository;
import beauty.beauty.stylist.repository.StylistServiceRepository;
import beauty.beauty.global.kakao.KakaoGeocodingService;
import beauty.beauty.user.entity.User;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 미용사 대시보드 단위 테스트 (Mockito 기반).
 * 통합 테스트는 H2가 MySQL POINT 타입을 미지원하여 컨텍스트 로드 실패.
 * Service 로직 검증에 집중한다.
 */
@ExtendWith(MockitoExtension.class)
class StylistDashboardServiceTest {

    @Mock private StylistProfileRepository stylistProfileRepository;
    @Mock private StylistServiceRepository stylistServiceRepository;
    @Mock private OperatingHoursRepository operatingHoursRepository;
    @Mock private SalonRepository salonRepository;
    @Mock private KakaoGeocodingService kakaoGeocodingService;
    @Mock private ReservationRepository reservationRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ReviewRepository reviewRepository;

    @InjectMocks private StylistServiceImpl stylistService;

    private static final Long USER_ID = 100L;
    private static final Long STYLIST_ID = 1L;

    private StylistProfile profile;
    private User customer;
    private StylistServiceItem service;

    @BeforeEach
    void setUp() {
        User stylistUser = User.builder().id(USER_ID).name("미용사").build();
        profile = StylistProfile.builder().id(STYLIST_ID).user(stylistUser).build();

        customer = User.builder().id(2L).name("홍길동").build();
        service = StylistServiceItem.builder().id(10L).name("커트").price(20000).duration(40).build();
    }

    private Reservation res(Long id, LocalDateTime at, Reservation.Status status, int price) {
        return Reservation.builder()
                .id(id).user(customer).stylistProfile(profile).service(service)
                .reservedAt(at).status(status).totalPrice(price)
                .build();
    }

    @Test
    @DisplayName("미용사 프로필이 없으면 EntityNotFoundException")
    void getDashboard_noProfile() {
        when(stylistProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stylistService.getDashboard(USER_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Repository 값들이 응답 DTO에 그대로 매핑된다")
    void getDashboard_mapsRepositoryValues() {
        when(stylistProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));

        // 예약 통계
        when(reservationRepository.countByStylistInRange(eq(STYLIST_ID), any(), any()))
                .thenReturn(3L, 7L); // 첫 호출 = 오늘, 두번째 호출 = 이번달
        when(reservationRepository.countByStylistProfileIdAndStatus(STYLIST_ID, Reservation.Status.PENDING))
                .thenReturn(2L);

        // 매출 통계
        when(paymentRepository.sumAmountByStylistAndStatusInRange(
                eq(STYLIST_ID), eq(Payment.PayStatus.PAID), any(), any()))
                .thenReturn(150000L);
        when(paymentRepository.sumAmountByStylistAndStatusInRange(
                eq(STYLIST_ID), eq(Payment.PayStatus.REFUNDED), any(), any()))
                .thenReturn(20000L);

        // 리뷰 통계
        when(reviewRepository.calcRatingStats(STYLIST_ID))
                .thenReturn(Collections.singletonList(new Object[]{new BigDecimal("4.55"), 8L}));

        // 최근 예약
        Reservation r1 = res(101L, LocalDateTime.now().plusDays(1), Reservation.Status.PENDING, 20000);
        Reservation r2 = res(102L, LocalDateTime.now().plusDays(2), Reservation.Status.CONFIRMED, 30000);
        when(reservationRepository.findRecentByStylistId(eq(STYLIST_ID), any(Pageable.class)))
                .thenReturn(List.of(r1, r2));

        StylistDashboardResponse res = stylistService.getDashboard(USER_ID);

        // 예약
        assertThat(res.getReservation().getTodayCount()).isEqualTo(3L);
        assertThat(res.getReservation().getMonthCount()).isEqualTo(7L);
        assertThat(res.getReservation().getPendingCount()).isEqualTo(2L);

        // 매출
        assertThat(res.getSales().getMonthRevenue()).isEqualTo(150000L);
        assertThat(res.getSales().getMonthRefund()).isEqualTo(20000L);

        // 리뷰: 평균은 소수1자리 반올림
        assertThat(res.getReview().getTotalCount()).isEqualTo(8L);
        assertThat(res.getReview().getAvgRating()).isEqualByComparingTo(new BigDecimal("4.6"));

        // 최근 예약
        assertThat(res.getRecentReservations()).hasSize(2);
        assertThat(res.getRecentReservations().get(0).getId()).isEqualTo(101L);
        assertThat(res.getRecentReservations().get(0).getCustomerName()).isEqualTo("홍길동");
        assertThat(res.getRecentReservations().get(0).getServiceName()).isEqualTo("커트");
        assertThat(res.getRecentReservations().get(0).getStatus()).isEqualTo(Reservation.Status.PENDING);
    }

    @Test
    @DisplayName("리뷰가 0건이면 평균 평점은 0, 카운트도 0")
    void getDashboard_noReviews() {
        when(stylistProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(reservationRepository.countByStylistInRange(anyLong(), any(), any())).thenReturn(0L);
        when(reservationRepository.countByStylistProfileIdAndStatus(anyLong(), any())).thenReturn(0L);
        when(paymentRepository.sumAmountByStylistAndStatusInRange(anyLong(), any(), any(), any())).thenReturn(0L);
        when(reviewRepository.calcRatingStats(STYLIST_ID))
                .thenReturn(Collections.singletonList(new Object[]{null, 0L}));
        when(reservationRepository.findRecentByStylistId(anyLong(), any(Pageable.class)))
                .thenReturn(List.of());

        StylistDashboardResponse res = stylistService.getDashboard(USER_ID);

        assertThat(res.getReview().getTotalCount()).isZero();
        assertThat(res.getReview().getAvgRating()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(res.getRecentReservations()).isEmpty();
    }

    @Test
    @DisplayName("calcRatingStats가 Double을 반환해도 BigDecimal 변환 가능")
    void getDashboard_doubleRatingAggregation() {
        when(stylistProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(reservationRepository.countByStylistInRange(anyLong(), any(), any())).thenReturn(0L);
        when(reservationRepository.countByStylistProfileIdAndStatus(anyLong(), any())).thenReturn(0L);
        when(paymentRepository.sumAmountByStylistAndStatusInRange(anyLong(), any(), any(), any())).thenReturn(0L);
        // H2/MySQL에서 AVG는 Double로 반환되는 경우 대비
        when(reviewRepository.calcRatingStats(STYLIST_ID))
                .thenReturn(Collections.singletonList(new Object[]{4.0, 2L}));
        when(reservationRepository.findRecentByStylistId(anyLong(), any(Pageable.class)))
                .thenReturn(List.of());

        StylistDashboardResponse res = stylistService.getDashboard(USER_ID);

        assertThat(res.getReview().getAvgRating()).isEqualByComparingTo(new BigDecimal("4.0"));
        assertThat(res.getReview().getTotalCount()).isEqualTo(2L);
    }
}
