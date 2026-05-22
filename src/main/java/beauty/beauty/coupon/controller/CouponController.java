package beauty.beauty.coupon.controller;

import beauty.beauty.coupon.dto.CouponResponse;
import beauty.beauty.coupon.dto.IssueCouponRequest;
import beauty.beauty.coupon.service.CouponService;
import beauty.beauty.global.annotation.LoginUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    // 미용사가 쿠폰 생성
    @PostMapping
    public ResponseEntity<Void> createCoupon(@LoginUserId Long userId,
                                             @RequestBody IssueCouponRequest request) {
        couponService.createCoupon(userId, request);
        return ResponseEntity.ok().build();
    }

    // 미용사가 자기 쿠폰을 특정 유저에게 발급
    @PostMapping("/{couponId}/grant/{targetUserId}")
    public ResponseEntity<Void> grantCoupon(@LoginUserId Long requesterId,
                                            @PathVariable Long couponId,
                                            @PathVariable Long targetUserId) {
        couponService.grantCoupon(requesterId, targetUserId, couponId);
        return ResponseEntity.ok().build();
    }

    // 내 쿠폰 목록 조회 (유저)
    @GetMapping("/me")
    public ResponseEntity<List<CouponResponse>> getMyCoupons(@LoginUserId Long userId) {
        return ResponseEntity.ok(couponService.getMyCoupons(userId));
    }

    // 내가 만든 쿠폰 목록 조회 (미용사)
    @GetMapping("/stylist")
    public ResponseEntity<List<CouponResponse>> getStylistCoupons(@LoginUserId Long userId) {
        return ResponseEntity.ok(couponService.getStylistCoupons(userId));
    }

    // 할인 금액 계산 (결제 전 미리 확인)
    @GetMapping("/discount")
    public ResponseEntity<Integer> calculateDiscount(@LoginUserId Long userId,
                                                     @RequestParam Long userCouponId,
                                                     @RequestParam int originalPrice) {
        return ResponseEntity.ok(couponService.calculateDiscount(userId, userCouponId, originalPrice));
    }

    // 쿠폰 사용 처리 (결제 완료 시점에 호출)
    @PostMapping("/use")
    public ResponseEntity<Void> useCoupon(@LoginUserId Long userId,
                                          @RequestParam Long userCouponId) {
        couponService.useCoupon(userId, userCouponId);
        return ResponseEntity.ok().build();
    }

    // 즐겨찾기 유저 전체에게 쿠폰 일괄 발급
    @PostMapping("/{couponId}/grant-to-favorites")
    public ResponseEntity<Integer> grantToFavorites(@LoginUserId Long userId,
                                                    @PathVariable Long couponId) {
        return ResponseEntity.ok(couponService.grantToFavorites(userId, couponId));
    }
}
