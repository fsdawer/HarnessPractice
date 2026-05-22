package beauty.beauty.coupon.service;


import beauty.beauty.coupon.dto.CouponResponse;
import beauty.beauty.coupon.dto.IssueCouponRequest;

import java.util.List;

public interface CouponService {

    // 1. 쿠폰 생성 (미용사/관리자가 쿠폰 자체를 만드는 것)
    void createCoupon(Long userId, IssueCouponRequest request);

    // 2. 유저에게 쿠폰 발급 (쿠폰 소유 미용사만 가능)
    void grantCoupon(Long requesterId, Long targetUserId, Long couponId);

    // 3. 내 쿠폰 목록 조회 (유저 — 미사용 + 유효기간 내)
    List<CouponResponse> getMyCoupons(Long userId);

    // 4. 미용사가 만든 쿠폰 목록 조회
    List<CouponResponse> getStylistCoupons(Long userId);

    // 5. 쿠폰 사용 처리 (결제 시 — 유효성 검증 + isUsed = true)
    void useCoupon(Long userId, Long userCouponId);

    // 6. 할인 금액 계산 (결제 전 미리 계산해서 프론트에 보여줄 때)
    int calculateDiscount(Long userId, Long userCouponId, int originalPrice);

    // 7. 즐겨찾기 유저 전체에게 일괄 발급
    int grantToFavorites(Long stylistUserId, Long couponId);
}
