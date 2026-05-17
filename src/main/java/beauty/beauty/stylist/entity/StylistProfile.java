package beauty.beauty.stylist.entity;

import beauty.beauty.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stylist_profiles", indexes = {
    // 평점순 정렬: ORDER BY rating DESC
    @Index(name = "idx_stylist_rating", columnList = "rating"),
    // 리뷰순 정렬: ORDER BY review_count DESC
    @Index(name = "idx_stylist_review_count", columnList = "review_count"),
    // 랭킹 조회 / 지역 필터: WHERE district = ?
    @Index(name = "idx_stylist_district", columnList = "district"),
})
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class StylistProfile {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salon_id")
    private Salon salon;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(nullable = false)
    private int experience = 0;

    @Builder.Default
    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal rating = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "review_count", nullable = false)
    private int reviewCount = 0;

    @Builder.Default
    @Column(name = "favorite_count", nullable = false)
    private int favoriteCount = 0;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
