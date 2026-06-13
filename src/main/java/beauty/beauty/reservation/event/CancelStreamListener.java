package beauty.beauty.reservation.event;

import beauty.beauty.notification.service.NotificationService;
import beauty.beauty.reservation.entity.Waiting;
import beauty.beauty.reservation.repository.WaitingRepository;
import beauty.beauty.reservation.service.WaitingService;
import beauty.beauty.global.config.RedisStreamConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CancelStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final WaitingRepository    waitingRepository;
    private final WaitingService       waitingService;
    private final NotificationService  notificationService;
    private final StringRedisTemplate  stringRedisTemplate;

    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;
    private Subscription subscription;

    @PostConstruct
    public void init() {
        try {
            boolean streamExists = Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisStreamConfig.CANCEL_STREAM_KEY));
            if (!streamExists) {
                stringRedisTemplate.opsForStream().createGroup(RedisStreamConfig.CANCEL_STREAM_KEY, RedisStreamConfig.CANCEL_CONSUMER_GROUP);
            }
        } catch (Exception e) {
            try {
                stringRedisTemplate.opsForStream().createGroup(RedisStreamConfig.CANCEL_STREAM_KEY, RedisStreamConfig.CANCEL_CONSUMER_GROUP);
            } catch (Exception ex) {
                // ignore
            }
        }

        this.subscription = listenerContainer.receive(
                Consumer.from(RedisStreamConfig.CANCEL_CONSUMER_GROUP, RedisStreamConfig.CANCEL_CONSUMER_NAME),
                StreamOffset.create(RedisStreamConfig.CANCEL_STREAM_KEY, ReadOffset.lastConsumed()),
                this
        );
    }

    @PreDestroy
    public void destroy() {
        if (subscription != null) subscription.cancel();
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            Map<String, String> value = message.getValue();
            Long stylistId = Long.parseLong(value.get("stylistId"));
            LocalDate date = LocalDate.parse(value.get("date"));
            LocalTime time = LocalTime.parse(value.get("time"));

            log.info("[Waiting Consumer] 빈자리 알림 이벤트 수신 — 미용사: {}, 날짜: {}, 시간: {}", stylistId, date, time);

            // [Flow 1] Chunk Pagination — OOM 방지를 위해 50명씩 끊어서 처리
            int page = 0;
            int size = 50;
            Page<Waiting> waitingPage;

            do {
                Pageable pageable = PageRequest.of(page, size);
                waitingPage = waitingRepository.findByStylistProfileIdAndWaitingDateAndWaitingTime(
                        stylistId, date, time, pageable);

                List<Waiting> chunk = waitingPage.getContent();
                for (Waiting waiting : chunk) {
                    // [Flow 2] 알림 발송
                    notificationService.notifyWaitingAvailable(waiting.getUser().getId(), date, time);
                }
                // [Flow 3] WaitingService를 통해 @Transactional 보장 하에 청크 삭제
                waitingService.deleteChunk(chunk);
                page++;
            } while (waitingPage.hasNext());

            // [Flow 4] 모든 알림 처리 완료 후 ACK
            stringRedisTemplate.opsForStream().acknowledge(
                    RedisStreamConfig.CANCEL_STREAM_KEY,
                    RedisStreamConfig.CANCEL_CONSUMER_GROUP,
                    message.getId());
            log.info("[Waiting Consumer] 알림 발송 및 ACK 완료 — MsgId: {}", message.getId());

        } catch (Exception e) {
            log.error("[Waiting Consumer] 빈자리 알림 처리 중 에러 발생", e);
        }
    }
}
