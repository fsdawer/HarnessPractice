package beauty.beauty.stylist.service;

import beauty.beauty.stylist.dto.SliceResponse;
import beauty.beauty.stylist.dto.*;
import beauty.beauty.stylist.entity.OperatingHours;
import beauty.beauty.stylist.entity.Salon;
import beauty.beauty.stylist.entity.StylistProfile;
import beauty.beauty.stylist.entity.StylistServiceItem;
import beauty.beauty.stylist.repository.OperatingHoursRepository;
import beauty.beauty.stylist.repository.SalonRepository;
import beauty.beauty.stylist.repository.StylistDailyStatRepository;
import beauty.beauty.stylist.repository.StylistProfileRepository;
import beauty.beauty.stylist.repository.StylistSearchSpec;
import beauty.beauty.stylist.repository.StylistServiceRepository;
import beauty.beauty.payment.entity.Payment;
import beauty.beauty.payment.repository.PaymentRepository;
import beauty.beauty.reservation.entity.Reservation;
import beauty.beauty.reservation.repository.ReservationRepository;
import beauty.beauty.review.repository.ReviewRepository;
import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.global.kakao.KakaoGeocodingService;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StylistServiceImpl implements StylistService {

    private final StylistProfileRepository stylistProfileRepository;
    private final StylistServiceRepository stylistServiceRepository;
    private final OperatingHoursRepository operatingHoursRepository;
    private final SalonRepository salonRepository;
    private final KakaoGeocodingService kakaoGeocodingService;
    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final ReviewRepository reviewRepository;
    private final StylistDailyStatRepository stylistDailyStatRepository;

    private static final GeometryFactory GEO_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Override
    @Transactional(readOnly = true)
    public SliceResponse<StylistProfileResponse> getStylists(
            String keyword, String district, String category,
            Integer minPrice, Integer maxPrice, String sort, int page, int size) {

        Specification<StylistProfile> spec = StylistSearchSpec.build(keyword, district, category, minPrice, maxPrice);

        Sort sortOrder = switch (sort != null ? sort : "rating") {
            case "review" -> Sort.by("reviewCount").descending();
            case "exp"    -> Sort.by("experience").descending();
            default       -> Sort.by("rating").descending();
        };

        var pageResult = stylistProfileRepository.findAll(spec, PageRequest.of(page, size, sortOrder));
        List<StylistProfileResponse> content = pageResult.getContent()
                .stream()
                .map(StylistProfileResponse::from)
                .toList();
        return new SliceResponse<>(content, page, size, pageResult.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public StylistProfileResponse getStylist(Long stylistId) {
        StylistProfile profile = stylistProfileRepository.findById(stylistId)
                .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_PROFILE_NOT_FOUND));
        return getDetailedProfileResponse(profile);
    }



    @Override
    @Transactional(readOnly = true)
    public StylistProfileResponse getMyProfile(Long userId) {
        StylistProfile profile = stylistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_PROFILE_NOT_FOUND));
        return getDetailedProfileResponse(profile);
    }



    private StylistProfileResponse getDetailedProfileResponse(StylistProfile profile) {
        List<ServiceResponse> services = stylistServiceRepository
                .findByStylistProfileIdAndIsActiveTrue(profile.getId())
                .stream().map(s -> ServiceResponse.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .category(s.getCategory())
                        .description(s.getDescription())
                        .price(s.getPrice())
                        .durationMinutes(s.getDuration())
                        .build())
                .collect(Collectors.toList());

        List<WorkingHoursResponse> workingHours = operatingHoursRepository
                .findByStylistProfileId(profile.getId())
                .stream().map(h -> WorkingHoursResponse.builder()
                        .id(h.getId())
                        .dayOfWeek(h.getDayOfWeek())
                        .openTime(h.getOpenTime())
                        .closeTime(h.getCloseTime())
                        .isDayOff(h.isClosed())
                        .build())
                .collect(Collectors.toList());

        return StylistProfileResponse.fromWithDetails(profile, services, workingHours);
    }

    @Override
    @Transactional
    public StylistProfileResponse updateProfile(Long userId, UpdateStylistProfileRequest request) {
        StylistProfile profile = stylistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_PROFILE_NOT_FOUND));

        if (request.getBio() != null) profile.setBio(request.getBio());
        if (request.getExperience() != null) profile.setExperience(request.getExperience());

        Salon salon = profile.getSalon();
        if (salon == null) {
            salon = salonRepository.save(new Salon());
            profile.setSalon(salon);
        }
        if (request.getSalonName() != null) salon.setName(request.getSalonName());
        if (request.getLocation() != null) {
            salon.setAddress(request.getLocation());
            double[] coords = kakaoGeocodingService.geocode(request.getLocation());
            if (coords != null) {
                // JTS Coordinate(x, y) = (longitude, latitude)
                salon.setLocation(GEO_FACTORY.createPoint(new Coordinate(coords[1], coords[0])));
            }
        }
        if (request.getSalonPhone() != null) salon.setPhone(request.getSalonPhone());
        if (request.getSalonDescription() != null) salon.setDescription(request.getSalonDescription());

        return StylistProfileResponse.from(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StylistProfileResponse> getNearbyStylists(double lat, double lng, int radius) {
        List<Long> ids = stylistProfileRepository.findNearbyIds(lat, lng, radius);
        if (ids.isEmpty()) return List.of();

        Map<Long, StylistProfile> profileMap = stylistProfileRepository.findByIdIn(ids)
                .stream().collect(Collectors.toMap(StylistProfile::getId, Function.identity()));

        // 거리 순서 유지
        return ids.stream()
                .map(profileMap::get)
                .filter(Objects::nonNull)
                .map(StylistProfileResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ServiceResponse addService(Long userId, ServiceRequest request) {
        StylistProfile profile = stylistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_PROFILE_NOT_FOUND));

        StylistServiceItem serviceItem = StylistServiceItem.builder()
                .stylistProfile(profile)
                .name(request.getName())
                .category(request.getCategory())
                .price(request.getPrice())
                .duration(request.getDurationMinutes())
                .description(request.getDescription())
                .build();

        StylistServiceItem saved = stylistServiceRepository.save(serviceItem);

        return ServiceResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .category(saved.getCategory())
                .price(saved.getPrice())
                .durationMinutes(saved.getDuration())
                .description(saved.getDescription())
                .build();
    }

    @Override
    @Transactional
    public ServiceResponse updateService(Long userId, Long serviceId, ServiceRequest request) {
        StylistProfile profile = stylistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_PROFILE_NOT_FOUND));

        StylistServiceItem serviceItem = stylistServiceRepository.findById(serviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_NOT_FOUND));

        if (!serviceItem.getStylistProfile().getId().equals(profile.getId())) {
            throw new CustomException(ErrorCode.SERVICE_NOT_AUTHORIZED);
        }

        serviceItem.update(
                request.getName(),
                request.getCategory(),
                request.getPrice(),
                request.getDurationMinutes(),
                request.getDescription()
        );

        StylistServiceItem updated = stylistServiceRepository.save(serviceItem);

        return ServiceResponse.builder()
                .id(updated.getId())
                .name(updated.getName())
                .category(updated.getCategory())
                .price(updated.getPrice())
                .durationMinutes(updated.getDuration())
                .description(updated.getDescription())
                .build();
    }

    @Override
    @Transactional
    public void deleteService(Long userId, Long serviceId) {
        StylistProfile profile = stylistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_PROFILE_NOT_FOUND));

        StylistServiceItem serviceItem = stylistServiceRepository.findById(serviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SERVICE_NOT_FOUND));

        if (!serviceItem.getStylistProfile().getId().equals(profile.getId())) {
            throw new CustomException(ErrorCode.SERVICE_NOT_AUTHORIZED);
        }

        serviceItem.setActive(false);
        stylistServiceRepository.save(serviceItem);
    }

    @Override
    @Transactional
    public WorkingHoursResponse updateHours(Long userId, WorkingHoursRequest request) {
        StylistProfile profile = stylistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_PROFILE_NOT_FOUND));

        OperatingHours operatingHours = operatingHoursRepository
                .findByStylistProfileIdAndDayOfWeek(profile.getId(), request.getDayOfWeek())
                .orElse(null);

        if (operatingHours == null) {
            operatingHours = OperatingHours.builder()
                    .stylistProfile(profile)
                    .dayOfWeek(request.getDayOfWeek())
                    .openTime(request.getOpenTime())
                    .closeTime(request.getCloseTime())
                    .isClosed(request.isDayOff())
                    .build();
        } else {
            operatingHours.update(request.getOpenTime(), request.getCloseTime(), request.isDayOff());
        }

        OperatingHours saved = operatingHoursRepository.save(operatingHours);

        return WorkingHoursResponse.builder()
                .id(saved.getId())
                .dayOfWeek(saved.getDayOfWeek())
                .openTime(saved.getOpenTime())
                .closeTime(saved.getCloseTime())
                .isDayOff(saved.isClosed())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public StylistDashboardResponse getDashboard(Long userId) {
        StylistProfile profile = stylistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_PROFILE_NOT_FOUND));
        Long stylistId = profile.getId();

        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime nextMonthStart = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();

        // 예약 통계
        long todayCount = reservationRepository.countByStylistInRange(stylistId, todayStart, tomorrowStart);
        long monthCount = reservationRepository.countByStylistInRange(stylistId, monthStart, nextMonthStart);
        long pendingCount = reservationRepository.countByStylistProfileIdAndStatus(stylistId, Reservation.Status.PENDING);

        // 매출 통계
        long monthRevenue = paymentRepository.sumAmountByStylistAndStatusInRange(
                stylistId, Payment.PayStatus.PAID, monthStart, nextMonthStart);
        long monthRefund = paymentRepository.sumAmountByStylistAndStatusInRange(
                stylistId, Payment.PayStatus.REFUNDED, monthStart, nextMonthStart);

        // 리뷰 통계 (기존 calcRatingStats 재사용: [AVG(rating), COUNT(*)])
        List<Object[]> stats = reviewRepository.calcRatingStats(stylistId);
        BigDecimal avgRating = BigDecimal.ZERO;
        long totalReviewCount = 0L;
        if (!stats.isEmpty() && stats.get(0)[0] != null) {
            Object avgObj = stats.get(0)[0];
            Object countObj = stats.get(0)[1];
            if (avgObj instanceof BigDecimal bd) {
                avgRating = bd.setScale(1, RoundingMode.HALF_UP);
            } else if (avgObj instanceof Number n) {
                avgRating = BigDecimal.valueOf(n.doubleValue()).setScale(1, RoundingMode.HALF_UP);
            }
            if (countObj instanceof Number n) {
                totalReviewCount = n.longValue();
            }
        }

        // 최근 예약 5건
        List<RecentReservationDto> recent = reservationRepository
                .findRecentByStylistId(stylistId, PageRequest.of(0, 5))
                .stream()
                .map(RecentReservationDto::from)
                .collect(Collectors.toList());

        // 최근 30일 일별 매출 통계
        LocalDate thirtyDaysAgo = today.minusDays(29);
        List<StylistDashboardResponse.DailyStatDto> dailyStats = stylistDailyStatRepository
                .findByStylistProfileIdAndStatDateBetweenOrderByStatDateAsc(stylistId, thirtyDaysAgo, today)
                .stream()
                .map(s -> StylistDashboardResponse.DailyStatDto.builder()
                        .date(s.getStatDate())
                        .revenue(s.getTotalRevenue())
                        .reservationCount(s.getReservationCount())
                        .build())
                .toList();

        return StylistDashboardResponse.builder()
                .reservation(StylistDashboardResponse.ReservationStats.builder()
                        .todayCount(todayCount)
                        .monthCount(monthCount)
                        .pendingCount(pendingCount)
                        .build())
                .sales(StylistDashboardResponse.SalesStats.builder()
                        .monthRevenue(monthRevenue)
                        .monthRefund(monthRefund)
                        .build())
                .review(StylistDashboardResponse.ReviewStats.builder()
                        .totalCount(totalReviewCount)
                        .avgRating(avgRating)
                        .build())
                .recentReservations(recent)
                .dailyStats(dailyStats)
                .build();
    }
}
