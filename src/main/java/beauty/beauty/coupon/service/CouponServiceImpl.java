package beauty.beauty.coupon.service;

import beauty.beauty.coupon.dto.CouponResponse;
import beauty.beauty.coupon.dto.IssueCouponRequest;
import beauty.beauty.coupon.entity.Coupon;
import beauty.beauty.coupon.entity.UserCoupon;
import beauty.beauty.coupon.repository.CouponRepository;
import beauty.beauty.coupon.repository.UserCouponRepository;
import beauty.beauty.stylist.entity.StylistProfile;
import beauty.beauty.stylist.repository.StylistProfileRepository;
import beauty.beauty.user.entity.User;
import beauty.beauty.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final UserCouponRepository userCouponRepository;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;
    private final StylistProfileRepository stylistProfileRepository;


    // 미용사가 쿠폰 생성
    @Override
    @Transactional
    public void createCoupon(Long userId, IssueCouponRequest request) {
        // 미용사인지 검증
        StylistProfile stylistProfile = stylistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("미용사 프로필이 없습니다"));

        couponRepository.save(Coupon.builder()
                        .code(request.getCode())
                        .name(request.getName())
                        .discountRate(request.getDiscountRate())
                        .minPrice(request.getMinPrice())
                        .maxDiscount(request.getMaxDiscount())
                        .startAt(request.getStartAt() != null ? request.getStartAt() : LocalDateTime.now())
                        .expiredAt(request.getExpiredAt())
                        .stylist(stylistProfile)
                        .build());
    }


    // 미용사가 자기 쿠폰을 특정 유저에게 발급
    @Override
    @Transactional
    public void grantCoupon(Long requesterId, Long targetUserId, Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 쿠폰"));

        if (coupon.getStylist() == null
                || !coupon.getStylist().getUser().getId().equals(requesterId)) {
            throw new IllegalArgumentException("해당 쿠폰의 소유 미용사만 발급할 수 있습니다");
        }

        if (userCouponRepository.existsByUserIdAndCouponId(targetUserId, couponId)) {
            throw new IllegalArgumentException("이미 발급된 쿠폰입니다");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자"));

        userCouponRepository.save(UserCoupon.builder()
                        .user(user)
                        .coupon(coupon)
                        .build());
    }


    // 내 쿠폰 목록 조회
    @Override
    public List<CouponResponse> getMyCoupons(Long userId) {
        return userCouponRepository.findValidCouponsByUserId(userId)
                .stream()
                .map(CouponResponse::from)
                .toList();
    }


    // 미용사가 만든 쿠폰 목록 조회
    @Override
    public List<CouponResponse> getStylistCoupons(Long userId) {

        StylistProfile stylistProfile = stylistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("미용사 프로필이 없습니다"));

        return couponRepository.findByStylistIdOrderByCreatedAtDesc(stylistProfile.getId())
                .stream()
                .map(CouponResponse::from)
                .toList();
    }



    // 쿠폰 사용처리
    @Override
    @Transactional
    public void useCoupon(Long userId, Long userCouponId) {
        UserCoupon userCoupon = userCouponRepository.findValidOne(userCouponId, userId)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 이미 사용된 쿠폰입니다."));

        userCoupon.use();
    }


    // 할인 금액 계산
    @Override
    public int calculateDiscount(Long userId, Long userCouponId, int originalPrice) {
        UserCoupon userCoupon = userCouponRepository.findValidOne(userCouponId, userId)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 쿠폰입니다."));

        Coupon coupon = userCoupon.getCoupon();

        if (originalPrice < coupon.getMinPrice()) {
            throw new IllegalArgumentException("최소 결제 금액이 충족되지 않았습니다.");
        }

        int discount = originalPrice * coupon.getDiscountRate() / 100;

        if (coupon.getMaxDiscount() != null) {
            discount = Math.min(discount, coupon.getMaxDiscount());
        }

        return discount;
    }
}
