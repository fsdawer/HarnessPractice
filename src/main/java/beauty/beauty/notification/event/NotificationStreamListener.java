package beauty.beauty.notification.event;

import beauty.beauty.global.config.RedisStreamConfig;
import beauty.beauty.notification.dto.NotificationMessage;
import beauty.beauty.notification.service.SseEmitterService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final SseEmitterService sseEmitterService;
    private final ObjectMapper      redisObjectMapper;
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;

    private Subscription subscription;

    @PostConstruct
    public void init() {
        StreamListener<String, MapRecord<String, String, String>> self = this;
        this.subscription = listenerContainer.receive(
                Consumer.from(RedisStreamConfig.NOTIFY_GROUP, RedisStreamConfig.NOTIFY_CONSUMER_NAME),
                StreamOffset.create(RedisStreamConfig.NOTIFY_STREAM_KEY, ReadOffset.lastConsumed()),
                self
        );
        log.info("[Notify Stream] NotificationStreamListener 구독 시작");
    }

    @PreDestroy
    public void destroy() {
        if (subscription != null) subscription.cancel();
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String recordId = message.getId().getValue();
        String payload  = message.getValue().get("payload");
        String userIdStr = message.getValue().get("userId");

        try {
            Long userId = Long.parseLong(userIdStr);
            NotificationMessage msg = redisObjectMapper.readValue(payload, NotificationMessage.class);
            msg.setStreamMessageId(recordId);

            boolean delivered = sseEmitterService.send(userId, msg);
            log.info("[Notify Stream] SSE 전송 {} - userId: {}, recordId: {}",
                    delivered ? "성공" : "실패(미연결)", userId, recordId);
            // ACK는 클라이언트가 POST /api/notifications/ack 호출 시 처리
        } catch (Exception e) {
            log.warn("[Notify Stream] 메시지 처리 실패 - recordId: {}", recordId, e);
        }
    }
}
