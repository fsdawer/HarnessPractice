package beauty.beauty.reservation.service;

import beauty.beauty.ranking.service.RankingService;
import beauty.beauty.notification.service.NotificationService;
import beauty.beauty.reservation.entity.Reservation;
import beauty.beauty.reservation.repository.ReservationRepository;
import beauty.beauty.reservation.event.ReservationStreamListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;

/**
 * [After 3] ReservationStreamListener 단위 테스트
 *
 * 테스트 목적:
 *   - Redis Stream 메시지 수신 시 예약 정보를 조회하여 랭킹 및 알림 로직이 호출되는지 검증
 *   - 예외 발생 시 ACK(Acknowledge) 처리 및 예외 전파 차단 로직 검증
 *
 * 비동기 처리는 단순한 "속도 개선"이 아닌 
 * "타 도메인 장애 격리(Fault Tolerance) 및 데이터 정합성 보장(Guaranteed Delivery)"을 위해 도입되었음을 검증.
 */
@ExtendWith(MockitoExtension.class)
class ReservationStreamListenerTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RankingService rankingService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    @InjectMocks
    private ReservationStreamListener listener;

    // ── 헬퍼 메서드 ────────────────────────────────
    private Reservation mockReservation() {
        Reservation reservation = mock(Reservation.class);
        var stylistProfile = mock(beauty.beauty.stylist.entity.StylistProfile.class);
        when(reservation.getStylistProfile()).thenReturn(stylistProfile);
        return reservation;
    }

    private MapRecord<String, String, String> mockMessage(String reservationId) {
        return StreamRecords.mapBacked(Map.of("reservationId", reservationId))
                .withStreamKey("reservation-events")
                .withId(RecordId.of("1620000000000-0"));
    }

    // ── 테스트 1: 정상 흐름 ─────────────────────────────────────────────────

    @Test
    @DisplayName("메시지 수신 시 DB 조회 후 rankingService, notificationService 호출 및 ACK 처리")
    void onMessage_successFlow() {
        // given
        String reservationIdStr = "1";
        MapRecord<String, String, String> message = mockMessage(reservationIdStr);
        Reservation reservation = mockReservation();

        when(reservationRepository.findByIdWithNotificationData(1L)).thenReturn(Optional.of(reservation));
        when(stringRedisTemplate.opsForStream()).thenReturn(streamOperations);

        // when
        listener.onMessage(message);

        // then
        verify(reservationRepository, times(1)).findByIdWithNotificationData(1L);
        verify(notificationService, times(1)).notifyReservationCreated(reservation);
        verify(streamOperations, times(1)).acknowledge(anyString(), eq(message));
    }

    // ── 테스트 2: 랭킹 실패해도 알림은 전송됨 ────────────────────────────────

    @Test
    @DisplayName("rankingService 예외 발생 시에도 격리되어 알림이 전송되고 ACK 처리됨")
    void onMessage_rankingFails_isolated() {
        // given
        MapRecord<String, String, String> message = mockMessage("1");
        Reservation reservation = mockReservation();

        when(reservationRepository.findByIdWithNotificationData(1L)).thenReturn(Optional.of(reservation));
        when(stringRedisTemplate.opsForStream()).thenReturn(streamOperations);

        doThrow(new RuntimeException("Redis 장애")).when(rankingService).recalculateScore(any());

        // when
        listener.onMessage(message);

        // then
        verify(notificationService, times(1)).notifyReservationCreated(reservation);
        verify(streamOperations, times(1)).acknowledge(anyString(), eq(message));
    }

    // ── 테스트 3: 예약 조회 실패 시 ────────────────────────────────

    @Test
    @DisplayName("DB에 예약 데이터가 없으면 예외를 로깅하고 ACK를 보내지 않아 데드레터 혹은 재시도 대상으로 남김")
    void onMessage_reservationNotFound_noAck() {
        // given
        MapRecord<String, String, String> message = mockMessage("999");
        when(reservationRepository.findByIdWithNotificationData(999L)).thenReturn(Optional.empty());

        // when
        listener.onMessage(message);

        // then
        verify(rankingService, never()).recalculateScore(any());
        verify(notificationService, never()).notifyReservationCreated(any());
        // 예외가 발생하므로 acknowledge는 호출되지 않아야 함 (데이터 정합성 보장을 위해 재처리 큐에 남김)
        verify(stringRedisTemplate, never()).opsForStream();
    }
}
