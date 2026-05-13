package beauty.beauty.coupon.entity;

import beauty.beauty.stylist.entity.StylistProfile;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupons", indexes = {
    @Index(name = "idx_coupon_code",    columnList = "code"),
    @Index(name = "idx_coupon_stylist", columnList = "stylist_id"),
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Coupon {


    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "discount_rate", nullable = false)
    private int discountRate;               // 1~100 (%)

    @Builder.Default
    @Column(name = "min_price", nullable = false)
    private int minPrice = 0;              // 최소 결제 금액

    @Column(name = "max_discount")
    private Integer maxDiscount;           // 최대 할인 상한 (null = 무제한)

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    // null이면 관리자 발급, 값이 있으면 해당 미용사 발급
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stylist_id")
    private StylistProfile stylist;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
