package beauty.beauty.coupon.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class IssueCouponRequest {

    private String code;           // 쿠폰 코드 (예: SUMMER10)
    private String name;           // 쿠폰 이름
    private int discountRate;      // 할인율 (1~100)
    private int minPrice;          // 최소 결제 금액
    private Integer maxDiscount;   // 최대 할인 상한 (null = 무제한)
    private LocalDateTime startAt;
    private LocalDateTime expiredAt;
}
