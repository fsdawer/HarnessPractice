package beauty.beauty.coupon.repository;

import beauty.beauty.coupon.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    // 미용사가 발급한 쿠폰 목록
    List<Coupon> findByStylistIdOrderByCreatedAtDesc(Long stylistId);

    // 회원가입 시 자동 발급용: 관리자(stylist_id IS NULL) 쿠폰 중 유효기간 내 첫 번째
    @Query("SELECT c FROM Coupon c WHERE c.stylist IS NULL AND c.startAt <= :now AND c.expiredAt > :now ORDER BY c.createdAt ASC")
    Optional<Coupon> findFirstActive(@org.springframework.data.repository.query.Param("now") LocalDateTime now);

    default Optional<Coupon> findFirstActive() {
        return findFirstActive(LocalDateTime.now());
    }
}
