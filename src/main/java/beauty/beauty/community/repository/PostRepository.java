package beauty.beauty.community.repository;

import beauty.beauty.community.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    // [Flow 1] 전체 탭 커서 쿼리: lastId 없으면 최신순 20개, 있으면 해당 id 미만
    @Query("SELECT p FROM Post p JOIN FETCH p.user LEFT JOIN FETCH p.reservation r LEFT JOIN FETCH r.stylistProfile sp LEFT JOIN FETCH sp.user LEFT JOIN FETCH r.service WHERE (:lastId IS NULL OR p.id < :lastId) ORDER BY p.id DESC LIMIT :size")
    List<Post> findAllCursor(@Param("lastId") Long lastId, @Param("size") int size);

    // [Flow 2] 우리동네 탭 커서 쿼리: district 필터 추가
    @Query("SELECT p FROM Post p JOIN FETCH p.user LEFT JOIN FETCH p.reservation r LEFT JOIN FETCH r.stylistProfile sp LEFT JOIN FETCH sp.user LEFT JOIN FETCH r.service WHERE p.district = :district AND (:lastId IS NULL OR p.id < :lastId) ORDER BY p.id DESC LIMIT :size")
    List<Post> findByDistrictCursor(@Param("district") String district, @Param("lastId") Long lastId, @Param("size") int size);
}
