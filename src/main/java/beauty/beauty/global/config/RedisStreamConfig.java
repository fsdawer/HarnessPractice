package beauty.beauty.global.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * [After 3] Redis Streams 비동기 메시지 큐 설정
 *
 * 기존 @Async 인메모리 스레드풀의 메시지 유실 한계를 극복하기 위해
 * Redis Streams 기반의 Pub/Sub 아키텍처를 도입합니다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    public static final String STREAM_KEY = "reservation-events";
    public static final String CONSUMER_GROUP = "reservation-group";
    public static final String CONSUMER_NAME = "instance-1";

    public static final String CANCEL_STREAM_KEY = "cancel_stream";
    public static final String CANCEL_CONSUMER_GROUP = "waiting-group";
    public static final String CANCEL_CONSUMER_NAME = "waiting-consumer-1";

    public static final String REFUND_STREAM_KEY = "reservation-cancel-refund-stream";
    public static final String REFUND_CONSUMER_GROUP = "refund-group";
    public static final String REFUND_CONSUMER_NAME = "refund-consumer-1";

    private final StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    public void initStreamAndGroup() {
        try {
            // Redis Streams에 Consumer Group이 존재하는지 확인, 없으면 생성
            boolean streamExists = Boolean.TRUE.equals(stringRedisTemplate.hasKey(STREAM_KEY));
            if (!streamExists) {
                // 스트림이 없으면 임시 메시지를 하나 넣어서 스트림을 생성한 뒤 그룹을 만듭니다.
                // (빈 스트림에는 그룹 생성이 안 될 수 있으므로 mkstream 옵션에 해당하는 명령어가 필요하지만,
                // Spring Data Redis에서는 그룹 생성 시 자동으로 스트림을 만드는 메서드를 제공합니다.)
                stringRedisTemplate.opsForStream().createGroup(STREAM_KEY, CONSUMER_GROUP);
                log.info("[Redis Stream] 스트림 및 컨슈머 그룹 생성 완료: {}", STREAM_KEY);
            } else {
                // 스트림은 존재하나 그룹이 없을 수 있으므로 그룹 조회
                var groups = stringRedisTemplate.opsForStream().consumers(STREAM_KEY, CONSUMER_GROUP);
                // 위 코드는 그룹이 없으면 에러를 던지므로 try-catch로 감쌉니다.
            }
        } catch (Exception e) {
            // 그룹이 이미 존재하는 예외 등은 무시하거나, 없으면 생성
            try {
                stringRedisTemplate.opsForStream().createGroup(STREAM_KEY, CONSUMER_GROUP);
                log.info("[Redis Stream] 기존 스트림에 컨슈머 그룹 생성 완료: {}", CONSUMER_GROUP);
            } catch (Exception ex) {
                // 이미 존재함 에러 무시
            }
        }

        // 예약 취소 → 자동 환불 Stream 초기화
        initStream(REFUND_STREAM_KEY, REFUND_CONSUMER_GROUP);
    }

    private void initStream(String streamKey, String groupName) {
        try {
            boolean streamExists = Boolean.TRUE.equals(stringRedisTemplate.hasKey(streamKey));
            if (!streamExists) {
                stringRedisTemplate.opsForStream().createGroup(streamKey, groupName);
                log.info("[Redis Stream] 스트림 및 컨슈머 그룹 생성 완료: {}", streamKey);
            }
        } catch (Exception e) {
            try {
                stringRedisTemplate.opsForStream().createGroup(streamKey, groupName);
                log.info("[Redis Stream] 기존 스트림에 컨슈머 그룹 생성 완료: {}", groupName);
            } catch (Exception ex) {
                // 이미 존재함 에러 무시
            }
        }
    }

    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            @Qualifier("reservationTaskExecutor") Executor executor) {

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .executor(executor)
                        .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }
}
