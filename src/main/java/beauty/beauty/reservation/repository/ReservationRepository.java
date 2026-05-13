package beauty.beauty.reservation.repository;

import beauty.beauty.reservation.entity.Reservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId);

    // [Offset 방식] — 유지 (미용사 예약 관리 등 내부 용도)
    @Query(value = "SELECT r FROM Reservation r " +
                   "JOIN FETCH r.user " +
                   "JOIN FETCH r.stylistProfile sp " +
                   "JOIN FETCH sp.user " +
                   "LEFT JOIN FETCH sp.salon " +
                   "JOIN FETCH r.service " +
                   "WHERE r.user.id = :userId " +
                   "ORDER BY r.createdAt DESC",
           countQuery = "SELECT COUNT(r) FROM Reservation r WHERE r.user.id = :userId")
    Page<Reservation> findByUserIdWithDetails(@Param("userId") Long userId, Pageable pageable);

    // [Cursor 방식] No-Offset — PK 범위 조건으로 항상 size개만 읽음
    // lastId=null 이면 첫 페이지, 이후엔 직전 페이지 마지막 id 전달
    @Query("SELECT r FROM Reservation r " +
           "JOIN FETCH r.user " +
           "JOIN FETCH r.stylistProfile sp " +
           "JOIN FETCH sp.user " +
           "LEFT JOIN FETCH sp.salon " +
           "JOIN FETCH r.service " +
           "WHERE r.user.id = :userId " +
           "AND (:lastId IS NULL OR r.id < :lastId) " +
           "ORDER BY r.id DESC")
    List<Reservation> findByUserIdCursor(@Param("userId") Long userId,
                                         @Param("lastId") Long lastId,
                                         Pageable pageable);



    List<Reservation> findByStylistProfileId(Long stylistId);

    // [Cursor 방식] 미용사 예약 목록 — No-Offset
    @Query("SELECT r FROM Reservation r " +
           "JOIN FETCH r.user " +
           "JOIN FETCH r.stylistProfile sp " +
           "JOIN FETCH sp.user " +
           "LEFT JOIN FETCH sp.salon " +
           "JOIN FETCH r.service " +
           "WHERE r.stylistProfile.id = :stylistId " +
           "AND (:lastId IS NULL OR r.id < :lastId) " +
           "ORDER BY r.id DESC")
    List<Reservation> findByStylistProfileIdCursor(@Param("stylistId") Long stylistId,
                                                    @Param("lastId") Long lastId,
                                                    Pageable pageable);
    List<Reservation> findByUserIdAndStatus(Long userId, Reservation.Status status);
    boolean existsByStylistProfileIdAndReservedAtAndStatusIn(Long stylistId, LocalDateTime reservedAt, List<Reservation.Status> statuses);
    List<Reservation> findByStylistProfileIdAndReservedAtBetweenAndStatusIn(Long stylistId, LocalDateTime start, LocalDateTime end, List<Reservation.Status> statuses);

    // recalculateScore 단건용
    @Query("SELECT COUNT(r) FROM Reservation r " +
           "WHERE r.stylistProfile.id = :stylistId " +
           "AND r.reservedAt >= :since " +
           "AND r.status IN :statuses")
    long countByStylistProfileIdAndReservedAtAfterAndStatusIn(
            @Param("stylistId") Long stylistId,
            @Param("since") LocalDateTime since,
            @Param("statuses") List<Reservation.Status> statuses);

    // getRanking 배치용: N번 쿼리 → 1번 GROUP BY
    @Query("SELECT r.stylistProfile.id, COUNT(r) FROM Reservation r " +
           "WHERE r.stylistProfile.id IN :stylistIds " +
           "AND r.reservedAt >= :since " +
           "AND r.status IN :statuses " +
           "GROUP BY r.stylistProfile.id")
    List<Object[]> countGroupByStylistIdsAndReservedAtAfterAndStatusIn(
            @Param("stylistIds") List<Long> stylistIds,
            @Param("since") LocalDateTime since,
            @Param("statuses") List<Reservation.Status> statuses);

    // 리마인더 스케줄러용: reservedAt 범위 내 CONFIRMED 예약 조회 + 알림에 필요한 연관관계 즉시 로딩
    @Query("SELECT r FROM Reservation r " +
           "JOIN FETCH r.user " +
           "JOIN FETCH r.stylistProfile sp " +
           "JOIN FETCH sp.user " +
           "WHERE r.reservedAt BETWEEN :from AND :to " +
           "AND r.status = :status")
    List<Reservation> findForReminder(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") Reservation.Status status);

    // 알림 전송용: user / stylistProfile.user 즉시 로딩 (LAZY 연관관계 @Async 컨텍스트 접근 대비)
    @Query("SELECT r FROM Reservation r " +
           "JOIN FETCH r.user " +
           "JOIN FETCH r.stylistProfile sp " +
           "JOIN FETCH sp.user " +
           "WHERE r.id = :id")
    Optional<Reservation> findByIdWithNotificationData(@Param("id") Long id);

    // [CRM 대시보드] 배치 집계용: 특정 날짜의 미용사별 통계 (상태별 그룹화)
    @Query("SELECT r.stylistProfile.id, r.status, COUNT(r), COALESCE(SUM(r.totalPrice), 0) " +
           "FROM Reservation r " +
           "WHERE r.reservedAt >= :start AND r.reservedAt < :end " +
           "GROUP BY r.stylistProfile.id, r.status")
    List<Object[]> getDailyStatsGroupByStylistAndStatus(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // [대시보드] 미용사 ID + reservedAt 범위로 예약 건수
    @Query("SELECT COUNT(r) FROM Reservation r " +
           "WHERE r.stylistProfile.id = :stylistId " +
           "AND r.reservedAt >= :start AND r.reservedAt < :end")
    long countByStylistInRange(@Param("stylistId") Long stylistId,
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    // [대시보드] 미용사 ID + status로 건수
    long countByStylistProfileIdAndStatus(Long stylistId, Reservation.Status status);

    // [대시보드] 최근 예약 N건 — fetch join (user, service)
    @Query("SELECT r FROM Reservation r " +
           "JOIN FETCH r.user " +
           "JOIN FETCH r.service " +
           "WHERE r.stylistProfile.id = :stylistId " +
           "ORDER BY r.createdAt DESC")
    List<Reservation> findRecentByStylistId(@Param("stylistId") Long stylistId,
                                             Pageable pageable);
}
