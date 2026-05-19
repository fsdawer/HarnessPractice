package beauty.beauty.stylist.repository;

import beauty.beauty.stylist.entity.StylistProfile;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StylistProfileRepository extends JpaRepository<StylistProfile, Long>, JpaSpecificationExecutor<StylistProfile> {

    @EntityGraph(attributePaths = {"user", "salon"})
    List<StylistProfile> findAll(Specification<StylistProfile> spec, Sort sort);

    Optional<StylistProfile> findByUserId(Long userId);

    @Query("SELECT s FROM StylistProfile s JOIN FETCH s.user LEFT JOIN FETCH s.salon WHERE s.salon.id = :salonId")
    List<StylistProfile> findBySalonId(@Param("salonId") Long salonId);


    // N+1 해결용: userId 목록으로 (userId, salonName) 쌍을 한 번에 조회
    @Query("SELECT s.user.id, s.salon.name FROM StylistProfile s LEFT JOIN s.salon WHERE s.user.id IN :userIds")
    List<Object[]> findSalonNamesByUserIds(@Param("userIds") Collection<Long> userIds);



    @Query("SELECT DISTINCT s FROM StylistProfile s " +
            "JOIN FETCH s.user u " +
            "LEFT JOIN FETCH s.salon sal WHERE " +
            "(:location IS NULL OR sal.address LIKE %:location%)")
    List<StylistProfile> searchStylists(@Param("location") String location);



    // FULLTEXT 검색: keyword 있을 때만 호출 (salons.name, users.name에 FULLTEXT 인덱스 필요)
    @Query(value = """
            SELECT DISTINCT sp.id FROM stylist_profiles sp
            JOIN users u ON sp.user_id = u.id
            LEFT JOIN salons sal ON sp.salon_id = sal.id
            WHERE (MATCH(sal.name) AGAINST(:keyword IN BOOLEAN MODE)
                OR MATCH(u.name) AGAINST(:keyword IN BOOLEAN MODE))
            AND (:location IS NULL OR sal.address LIKE CONCAT('%', :location, '%'))
            """, nativeQuery = true)
    List<Long> searchStylistIdsByFulltext(@Param("keyword") String keyword, @Param("location") String location);


    // 랭킹 조회용: 특정 구의 미용사 전체 조회 (Before: N+1 기반 랭킹 계산에 사용)
    @Query("SELECT DISTINCT sp FROM StylistProfile sp " +
           "JOIN FETCH sp.user u " +
           "LEFT JOIN FETCH sp.salon s " +
           "WHERE s.district = :district")
    List<StylistProfile> findByDistrict(@Param("district") String district);


    // 위치 기반 반경 검색: 거리 오름차순으로 ID 반환 (radius 단위: 미터)
    @Query(value = """
            SELECT sp.id FROM stylist_profiles sp
            JOIN salons s ON sp.salon_id = s.id
            WHERE s.location IS NOT NULL
            AND ST_Distance_Sphere(s.location,
                ST_GeomFromText(CONCAT('POINT(', :lat, ' ', :lng, ')'), 4326)) <= :radius
            ORDER BY ST_Distance_Sphere(s.location,
                ST_GeomFromText(CONCAT('POINT(', :lat, ' ', :lng, ')'), 4326))
            LIMIT 20
            """, nativeQuery = true)
    List<Long> findNearbyIds(@Param("lat") double lat,
                             @Param("lng") double lng,
                             @Param("radius") int radius);

    @Query("SELECT DISTINCT sp FROM StylistProfile sp " +
           "JOIN FETCH sp.user u " +
           "LEFT JOIN FETCH sp.salon s " +
           "WHERE sp.id IN :ids")
    List<StylistProfile> findByIdIn(@Param("ids") List<Long> ids);


    // 찜하기 (Row Lock 기반 동시성 방어)
    @Modifying(clearAutomatically = true)
    @Query("UPDATE StylistProfile s SET s.favoriteCount = s.favoriteCount + 1 WHERE s.id = :id")
    void incrementFavoriteCount(@Param("id") Long id);



    // 찜 취소
    @Modifying(clearAutomatically = true)
    @Query("UPDATE StylistProfile s SET s.favoriteCount = s.favoriteCount - 1 WHERE s.id = :id AND s.favoriteCount > 0")
    void decrementFavoriteCount(@Param("id") Long id);
}
