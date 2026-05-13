package beauty.beauty.stylist.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class StylistDashboardResponse {

    private ReservationStats reservation;
    private SalesStats sales;
    private ReviewStats review;
    private List<RecentReservationDto> recentReservations;

    @Getter
    @Builder
    public static class ReservationStats {
        private long todayCount;     // 오늘 예약 수
        private long monthCount;     // 이번 달 예약 수
        private long pendingCount;   // 대기 중(PENDING) 예약 수
    }

    @Getter
    @Builder
    public static class SalesStats {
        private long monthRevenue;   // 이번 달 PAID 합계
        private long monthRefund;    // 이번 달 REFUNDED 합계
    }

    @Getter
    @Builder
    public static class ReviewStats {
        private long totalCount;     // 총 리뷰 수
        private BigDecimal avgRating; // 평균 평점
    }
}
