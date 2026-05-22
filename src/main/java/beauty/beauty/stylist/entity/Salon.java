package beauty.beauty.stylist.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "salons", indexes = {
    // 지역별 랭킹 조회: WHERE district = ?
    @Index(name = "idx_salon_district", columnList = "district"),
    // name LIKE '검색어%' 패턴 시 prefix 인덱스 활용 가능 (중간 LIKE는 불가)
    @Index(name = "idx_salon_name", columnList = "name"),
    // SPATIAL INDEX는 JPA로 생성 불가 → sql/add_spatial_index.sql 참고
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Salon {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String address;

    @Column(length = 50)
    private String district; // 구 단위 (예: 강남구, 마포구)

    @Column(length = 20)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private Point location; // 미용실 위치 (경도, 위도)

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
