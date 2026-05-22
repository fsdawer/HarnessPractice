package beauty.beauty.notification.repository;

import beauty.beauty.notification.entity.NotificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {

    List<NotificationHistory> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("UPDATE NotificationHistory n SET n.isRead = true WHERE n.userId = :userId")
    void markAllReadByUserId(@Param("userId") Long userId);

    long countByUserIdAndIsRead(Long userId, boolean isRead);
}
