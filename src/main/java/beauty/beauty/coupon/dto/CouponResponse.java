package beauty.beauty.coupon.dto;

import beauty.beauty.coupon.entity.Coupon;
import beauty.beauty.coupon.entity.UserCoupon;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CouponResponse {

    private Long userCouponId;   // 쿠폰 사용 요청 시 식별자
    private String code;
    private String name;
    private Integer discountRate;
    private Integer minPrice;
    private Integer maxDiscount;
    private LocalDateTime expiredAt;

    public static CouponResponse from(UserCoupon uc) {
        return CouponResponse.builder()
                .userCouponId(uc.getId())
                .code(uc.getCoupon().getCode())
                .name(uc.getCoupon().getName())
                .discountRate(uc.getCoupon().getDiscountRate())
                .minPrice(uc.getCoupon().getMinPrice())
                .maxDiscount(uc.getCoupon().getMaxDiscount())
                .expiredAt(uc.getCoupon().getExpiredAt())
                .build();
    }

    public static CouponResponse from(Coupon c) {
        return CouponResponse.builder()
                .userCouponId(c.getId())
                .code(c.getCode())
                .name(c.getName())
                .discountRate(c.getDiscountRate())
                .minPrice(c.getMinPrice())
                .maxDiscount(c.getMaxDiscount())
                .expiredAt(c.getExpiredAt())
                .build();
    }
}
