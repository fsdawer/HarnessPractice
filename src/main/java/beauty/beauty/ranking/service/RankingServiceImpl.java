package beauty.beauty.ranking.service;

import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.ranking.dto.RankingResponse;
import beauty.beauty.reservation.entity.Reservation;
import beauty.beauty.reservation.repository.ReservationRepository;
import beauty.beauty.review.repository.ReviewRepository;
import beauty.beauty.stylist.entity.StylistProfile;
import beauty.beauty.stylist.repository.StylistProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingServiceImpl implements RankingService {

    private final StylistProfileRepository stylistProfileRepository;
    private final ReviewRepository reviewRepository;
    private final ReservationRepository reservationRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String RANKING_KEY_PREFIX = "ranking:";
    private static final int TOP_N = 10;

    // [입력] district(구 이름 예: "강남구")
    // [흐름]
    // 1. Redis ZSET "ranking:강남구"에서 점수 높은 순으로 상위 10개 꺼냄
    // 2. 캐시 없으면 → warmUpAndGet()으로 DB 조회 후 Redis에 채우고 반환
    // 3. 캐시 있으면 → stylistId 목록으로 미용사 정보, 리뷰 수, 최근 예약 수를 DB에서 한 번에 조회
    // 4. ZSET 순서(점수 내림차순) 그대로 RankingResponse 리스트 조립 후 반환
    @Override
    @Transactional(readOnly = true)
    public List<RankingResponse> getRanking(String district) {
        String key = RANKING_KEY_PREFIX + district;

        // Redis ZSET에서 점수 내림차순으로 상위 10개 (stylistId, score) 쌍으로 꺼냄
        Set<ZSetOperations.TypedTuple<Object>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, TOP_N - 1);

        // 캐시 미스 → DB에서 직접 계산 후 Redis 채우고 반환
        if (tuples == null || tuples.isEmpty()) {
            log.info("[Ranking] 캐시 미스 — district={}, DB에서 워밍업", district);
            return warmUpAndGet(district);
        }

        // 캐시 히트 → ZSET에서 꺼낸 stylistId 목록으로 미용사 정보 한 번에 조회 (N+1 방지)
        List<Long> stylistIds = tuples.stream()
                .map(t -> Long.parseLong((String) t.getValue()))
                .toList();

        List<StylistProfile> stylists = stylistProfileRepository.findAllById(stylistIds);
        // id → StylistProfile 맵으로 변환 (아래 루프에서 O(1) 접근용)
        Map<Long, StylistProfile> stylistMap = stylists.stream()
                .collect(Collectors.toMap(StylistProfile::getId, s -> s));

        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<Reservation.Status> validStatuses = List.of(Reservation.Status.CONFIRMED, Reservation.Status.DONE);

        // 미용사 전체 리뷰 수 한 번에 조회 → Map<stylistId, reviewCount>
        Map<Long, Integer> reviewCountMap = reviewRepository.countGroupByStylistIds(stylistIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Long) row[1]).intValue()));

        // 최근 30일 예약 수 한 번에 조회 → Map<stylistId, bookingCount>
        Map<Long, Long> bookingCountMap = reservationRepository
                .countGroupByStylistIdsAndReservedAtAfterAndStatusIn(stylistIds, since, validStatuses).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        // ZSET 점수 순서 유지하면서 응답 조립
        List<RankingResponse> result = new ArrayList<>();
        for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
            Long stylistId = Long.parseLong((String) tuple.getValue());
            StylistProfile sp = stylistMap.get(stylistId);
            if (sp == null) continue;

            int reviewCount = reviewCountMap.getOrDefault(stylistId, 0);
            int recentBookings = bookingCountMap.getOrDefault(stylistId, 0L).intValue();
            double score = tuple.getScore() != null ? tuple.getScore() : 0.0;

            result.add(RankingResponse.from(sp, score, reviewCount, recentBookings));
        }

        return result;
    }

    // [입력] stylistProfileId (예약 생성 시 Redis Stream → 컨슈머에서 호출)
    // [흐름]
    // 1. stylistProfileId로 미용사 조회
    // 2. 해당 미용사의 전체 리뷰 수 조회
    // 3. 최근 30일 CONFIRMED/DONE 예약 수 조회
    // 4. 베이지안 점수 계산
    // 5. 미용사가 속한 구(district) 기준으로 Redis ZSET에 점수 업데이트 (ZADD)
    @Override
    @Transactional(readOnly = true)
    public void recalculateScore(Long stylistProfileId) {
        String lockKey = "lock:ranking:" + stylistProfileId;
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", 3, TimeUnit.SECONDS);
        if (!acquired) {
            log.debug("[Ranking] 락 획득 실패, 스킵 — stylistId={}", stylistProfileId);
            return;
        }
        try {
            StylistProfile stylistProfile = stylistProfileRepository.findById(stylistProfileId)
                    .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_PROFILE_NOT_FOUND));

            int reviewCount = reviewRepository.countByStylistProfileId(stylistProfile.getId());

            LocalDateTime since = LocalDateTime.now().minusDays(30);
            List<Reservation.Status> validStatuses = List.of(Reservation.Status.CONFIRMED, Reservation.Status.DONE);
            long recentBookings = reservationRepository.countByStylistProfileIdAndReservedAtAfterAndStatusIn(
                    stylistProfile.getId(), since, validStatuses);

            double score = computeBayesianScore(
                    reviewCount, stylistProfile.getRating().doubleValue(), (int) recentBookings);

            String district = stylistProfile.getSalon() != null ? stylistProfile.getSalon().getDistrict() : null;
            if (district != null) {
                String key = RANKING_KEY_PREFIX + district;
                redisTemplate.opsForZSet().add(key, String.valueOf(stylistProfile.getId()), score);
                log.debug("[Ranking] ZADD — stylistId={}, district={}, score={}", stylistProfile.getId(), district, score);
            }
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    // [입력] district (getRanking에서 캐시 미스 시 호출)
    // [흐름]
    // 1. 해당 구 소속 미용사 전체 DB 조회
    // 2. 전체 리뷰 수 / 최근 30일 예약 수 한 번에 조회
    // 3. 미용사별 베이지안 점수 계산 → Redis ZSET에 저장 (워밍업)
    // 4. 점수 내림차순 정렬 후 상위 10개만 반환
    @Transactional(readOnly = true)
    protected List<RankingResponse> warmUpAndGet(String district) {
        List<StylistProfile> stylists = stylistProfileRepository.findByDistrict(district);
        if (stylists.isEmpty()) return List.of();

        List<Long> stylistIds = stylists.stream().map(StylistProfile::getId).toList();
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<Reservation.Status> validStatuses = List.of(Reservation.Status.CONFIRMED, Reservation.Status.DONE);
        String key = RANKING_KEY_PREFIX + district;

        // 리뷰 수 / 예약 수 각각 GROUP BY 쿼리 1번으로 전체 조회
        Map<Long, Integer> reviewCountMap = reviewRepository.countGroupByStylistIds(stylistIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Long) row[1]).intValue()));
        Map<Long, Long> bookingCountMap = reservationRepository
                .countGroupByStylistIdsAndReservedAtAfterAndStatusIn(stylistIds, since, validStatuses).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        List<RankingResponse> result = new ArrayList<>();
        for (StylistProfile sp : stylists) {
            int reviewCount = reviewCountMap.getOrDefault(sp.getId(), 0);
            int recentBookings = bookingCountMap.getOrDefault(sp.getId(), 0L).intValue();
            double score = computeBayesianScore(reviewCount, sp.getRating().doubleValue(), recentBookings);

            // 계산된 점수를 Redis ZSET에 저장 (이후 요청은 캐시 히트)
            redisTemplate.opsForZSet().add(key, String.valueOf(sp.getId()), score);
            result.add(RankingResponse.from(sp, score, reviewCount, recentBookings));
        }

        result.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return result.stream().limit(TOP_N).toList();
    }

    // [입력] reviewCount(리뷰 수), avgRating(평균 평점), recentBookings(최근 30일 예약 수)
    // [흐름]
    // 베이지안 평균: 리뷰가 적을수록 전체 평균으로 수렴 (리뷰 5개 미만이면 점수 낮게)
    // 예약 보너스: 최근 예약 수 최대 200건 상한으로 0.3점씩 가산
    // 최종 점수 = 베이지안 점수 + 예약 보너스
    private double computeBayesianScore(int reviewCount, double avgRating, int recentBookings) {
        double bayesian = (avgRating * reviewCount) / (reviewCount + 5.0) * 40;
        double bookingBonus = Math.min(recentBookings, 200) * 0.3;
        return bayesian + bookingBonus;
    }
}
