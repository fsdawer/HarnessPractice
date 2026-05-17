package beauty.beauty.community.dto;

import beauty.beauty.community.entity.Post;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class PostResponse {

    private Long id;
    private String authorName;
    private Long authorId;
    private String title;
    private String content;
    private String district;
    private Integer price;
    private int viewCount;
    private int likeCount;
    private boolean likedByMe;
    private ReservationSummary reservationSummary;
    private LocalDateTime createdAt;

    // [입력] Post 엔티티 + likedByMe 여부
    // [흐름] 정적 팩토리로 DTO 변환
    public static PostResponse from(Post post, boolean likedByMe) {
        PostResponse dto = new PostResponse();
        dto.id = post.getId();
        dto.authorName = post.getUser().getName();
        dto.authorId = post.getUser().getId();
        dto.title = post.getTitle();
        dto.content = post.getContent();
        dto.district = post.getDistrict();
        dto.price = post.getPrice();
        dto.viewCount = post.getViewCount();
        dto.likeCount = post.getLikeCount();
        dto.likedByMe = likedByMe;
        dto.createdAt = post.getCreatedAt();

        if (post.getReservation() != null) {
            var res = post.getReservation();
            dto.reservationSummary = new ReservationSummary(
                res.getId(),
                res.getStylistProfile().getUser().getName(),
                res.getService().getName(),
                res.getReservedAt()
            );
        }

        return dto;
    }

    @Getter
    public static class ReservationSummary {
        private final Long reservationId;
        private final String stylistName;
        private final String serviceName;
        private final LocalDateTime reservedAt;

        public ReservationSummary(Long reservationId, String stylistName, String serviceName, LocalDateTime reservedAt) {
            this.reservationId = reservationId;
            this.stylistName = stylistName;
            this.serviceName = serviceName;
            this.reservedAt = reservedAt;
        }
    }
}
