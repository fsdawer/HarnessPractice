package beauty.beauty.review.service;

import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.reservation.entity.Reservation;
import beauty.beauty.reservation.repository.ReservationRepository;
import beauty.beauty.review.dto.ReviewRequest;
import beauty.beauty.review.dto.ReviewResponse;
import beauty.beauty.review.dto.ReviewUpdateRequest;
import beauty.beauty.review.dto.SalonReviewsResponse;
import beauty.beauty.review.entity.Review;
import beauty.beauty.review.repository.ReviewRepository;
import beauty.beauty.stylist.entity.StylistProfile;
import beauty.beauty.stylist.repository.StylistProfileRepository;
import beauty.beauty.user.entity.User;
import beauty.beauty.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final StylistProfileRepository stylistProfileRepository;

    // 리뷰 작성 — status=DONE 인 예약만 가능, 예약 1건당 1개
    // 유저 아이디를 파라미터로 넘겨 받았으니까 유저 아이디를 디비에서 확인할 필요 없고
    // 본인 예약인지만 확인하면 됨
    @Override
    @Transactional
    public ReviewResponse create(Long userId, ReviewRequest request) {
         Reservation reservation =  reservationRepository.findById(request.getReservationId())
                 .orElseThrow(() -> new IllegalArgumentException("예약이 없습니다"));

         // 완료된 예약인지 확인
        if(reservation.getStatus() != Reservation.Status.DONE) {
            throw new CustomException(ErrorCode.INVALID_RESERVATION_STATUS);
        }

        // 본인 예약인지 확인
        if(!reservation.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        // 이미 리뷰 있으면 작성 금지 (중복 방지)
        // 리뷰 DB에 중복된 예약 아이디가 있는지 조회 파라미터로 요청에 담긴 예약아이디를 같이 넘김
        if(reviewRepository.findByReservationId(request.getReservationId()).isPresent())
            throw new CustomException(ErrorCode.REVIEW_ALREADY_EXISTS);

        // 리뷰 저장
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Review review = Review.builder()
                .reservation(reservation)
                .user(user)
                .stylistProfile(reservation.getStylistProfile())
                .rating(request.getRating())
                .content(request.getContent())
                .build();

        reviewRepository.save(review);

        // 6. 미용사 평점/리뷰수 갱신
        recalculateRating(reservation.getStylistProfile());

        return ReviewResponse.from(review);
    }


    // 미용사별 리뷰 목록
    @Override
    @Transactional(readOnly = true)
    public List<ReviewResponse> getByStylist(Long stylistId) {
        return reviewRepository.findByStylistProfileId(stylistId)
                .stream()
                .map(ReviewResponse::from)
                .toList();

    }

    // 미용실 내 미용사별 리뷰 묶음 조회
    @Override
    @Transactional(readOnly = true)
    public List<SalonReviewsResponse> getBySalon(Long salonId) {
        // 1. 미용실 소속 미용사 목록
        List<StylistProfile> stylists = stylistProfileRepository.findBySalonId(salonId);

        // 2. 미용사 id 목록으로 리뷰 한 번에 조회
        List<Long> stylistIds = stylists.stream()
                .map(StylistProfile::getId)
                .toList();

        List<Review> allReviews = reviewRepository.findByStylistProfileIdIn(stylistIds);

        // 3. stylistId 기준으로 그룹핑
        Map<Long, List<Review>> grouped = allReviews.stream()
                .collect(Collectors.groupingBy(r -> r.getStylistProfile().getId()));

        // 4. 미용사별 응답 생성
        return stylists.stream()
                .map(stylist -> {
                    List<ReviewResponse> reviews = grouped
                            .getOrDefault(stylist.getId(), List.of())
                            .stream()
                            .map(ReviewResponse::from)
                            .toList();
                    return SalonReviewsResponse.from(stylist, reviews);
                })
                .toList();
    }



    // 리뷰 수정
    @Override
    @Transactional
    public ReviewResponse update(Long userId, Long reviewId, ReviewUpdateRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));

        if(!review.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        if (request.getRating() != null) review.setRating(BigDecimal.valueOf(request.getRating()));
        if (request.getContent() != null) review.setContent(request.getContent());

        // rating 바뀌었으면 평점 재계산
        if (request.getRating() != null) {
            recalculateRating(review.getStylistProfile());
        }

        return ReviewResponse.from(review);
    }



    // 내 리뷰 목록
    @Override
    @Transactional(readOnly = true)
    public List<ReviewResponse> getMyReviews(Long userId) {
        return reviewRepository.findByUserIdWithDetails(userId)
                .stream()
                .map(ReviewResponse::from)
                .toList();
    }

    // 리뷰 삭제
    @Override
    @Transactional
    public void delete(Long userId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_NOT_FOUND));

        if (!review.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        StylistProfile stylistProfile = review.getStylistProfile();
        review.setDeletedAt(LocalDateTime.now());
        recalculateRating(stylistProfile);
    }


    private void recalculateRating(StylistProfile stylistProfile) {
        List<Object[]> results = reviewRepository.calcRatingStats(stylistProfile.getId());
        Object[] stats = (results != null && !results.isEmpty()) ? results.get(0) : null;

        if (stats == null || stats.length < 2 || stats[1] == null) {
            stylistProfile.setRating(BigDecimal.ZERO);
            stylistProfile.setReviewCount(0);
            return;
        }

        long count = ((Number) stats[1]).longValue();
        if (count == 0) {
            stylistProfile.setRating(BigDecimal.ZERO);
            stylistProfile.setReviewCount(0);
        } else {
            BigDecimal avg = new BigDecimal(stats[0].toString()).setScale(1, RoundingMode.HALF_UP);
            stylistProfile.setRating(avg);
            stylistProfile.setReviewCount((int) count);
        }
    }
}
