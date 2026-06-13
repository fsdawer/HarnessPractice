package beauty.beauty.notification.controller;

import beauty.beauty.global.config.RedisStreamConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final StringRedisTemplate stringRedisTemplate;

    @PostMapping("/ack")
    public ResponseEntity<Void> ack(@RequestBody AckRequest req) {
        try {
            stringRedisTemplate.opsForStream().acknowledge(
                    RedisStreamConfig.NOTIFY_STREAM_KEY,
                    RedisStreamConfig.NOTIFY_GROUP,
                    req.messageId()
            );
        } catch (Exception ignored) {}
        return ResponseEntity.ok().build();
    }

    record AckRequest(String messageId) {}
}
