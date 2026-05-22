package beauty.beauty.favorite.repository;

import beauty.beauty.favorite.entity.FavoriteStylist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoriteStylistRepository extends JpaRepository<FavoriteStylist, Long> {
    Optional<FavoriteStylist> findByUserIdAndStylistProfileId(Long userId, Long stylistProfileId);
    boolean existsByUserIdAndStylistProfileId(Long userId, Long stylistProfileId);

    @Query("SELECT fs FROM FavoriteStylist fs " +
           "JOIN FETCH fs.stylistProfile sp " +
           "JOIN FETCH sp.user " +
           "LEFT JOIN FETCH sp.salon " +
           "WHERE fs.user.id = :userId " +
           "ORDER BY fs.createdAt DESC")
    List<FavoriteStylist> findByUserIdWithDetails(@Param("userId") Long userId);

    @Query("SELECT fs FROM FavoriteStylist fs JOIN FETCH fs.user WHERE fs.stylistProfile.id = :stylistProfileId")
    List<FavoriteStylist> findByStylistProfileIdWithUser(@Param("stylistProfileId") Long stylistProfileId);
}
