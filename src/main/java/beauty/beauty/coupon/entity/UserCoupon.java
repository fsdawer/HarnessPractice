package beauty.beauty.coupon.entity;

import beauty.beauty.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_coupons", indexes = {
    @Index(name = "idx_uc_user", columnList = "user_id"),
},
uniqueConstraints = {
    @UniqueConstraint(name = "uq_user_coupon", columnNames = {"user_id", "coupon_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserCoupon {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Builder.Default
    @Column(name = "is_used", nullable = false)
    private boolean isUsed = false;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Builder.Default
    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt = LocalDateTime.now();

    public void use() {
        this.isUsed = true;
        this.usedAt = LocalDateTime.now();
    }

    public void restore() {
        this.isUsed = false;
        this.usedAt = null;
    }
}
