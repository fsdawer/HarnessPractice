package beauty.beauty.stylist.dto;

import beauty.beauty.stylist.entity.Salon;
import beauty.beauty.stylist.entity.StylistProfile;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class StylistProfileResponse {

    private Long id;
    private Long userId;
    private String name;
    private String profileImg;
    private String bio;
    private int experience;
    private BigDecimal rating;
    private int reviewCount;
    private int favoriteCount;

    // 소속 미용실 정보
    private String salonName;
    private String location;         // 미용실 주소 (프론트 호환 유지)
    private String salonPhone;
    private String salonDescription;
    private Double latitude;
    private Double longitude;

    private List<ServiceResponse> services;
    private List<WorkingHoursResponse> workingHours;

    public static StylistProfileResponse from(StylistProfile profile) {
        Salon salon = profile.getSalon();
        return StylistProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUser().getId())
                .name(profile.getUser().getName())
                .profileImg(profile.getUser().getProfileImg())
                .bio(profile.getBio())
                .experience(profile.getExperience())
                .rating(profile.getRating())
                .reviewCount(profile.getReviewCount())
                .favoriteCount(profile.getFavoriteCount())
                .salonName(salon != null ? salon.getName() : null)
                .location(salon != null ? salon.getAddress() : null)
                .salonPhone(salon != null ? salon.getPhone() : null)
                .salonDescription(salon != null ? salon.getDescription() : null)
                .latitude(salon != null && salon.getLocation() != null ? salon.getLocation().getY() : null)
                .longitude(salon != null && salon.getLocation() != null ? salon.getLocation().getX() : null)
                .build();
    }

    public static StylistProfileResponse fromWithDetails(StylistProfile profile,
                                              List<ServiceResponse> services,
                                              List<WorkingHoursResponse> workingHours) {
        Salon salon = profile.getSalon();
        return StylistProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUser().getId())
                .name(profile.getUser().getName())
                .profileImg(profile.getUser().getProfileImg())
                .bio(profile.getBio())
                .experience(profile.getExperience())
                .rating(profile.getRating())
                .reviewCount(profile.getReviewCount())
                .favoriteCount(profile.getFavoriteCount())
                .salonName(salon != null ? salon.getName() : null)
                .location(salon != null ? salon.getAddress() : null)
                .salonPhone(salon != null ? salon.getPhone() : null)
                .salonDescription(salon != null ? salon.getDescription() : null)
                .latitude(salon != null && salon.getLocation() != null ? salon.getLocation().getY() : null)
                .longitude(salon != null && salon.getLocation() != null ? salon.getLocation().getX() : null)
                .services(services)
                .workingHours(workingHours)
                .build();
    }
}
