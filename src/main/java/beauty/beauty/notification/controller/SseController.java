package beauty.beauty.notification.controller;

import beauty.beauty.global.config.RedisStreamConfig;
import beauty.beauty.notification.dto.NotificationMessage;
import beauty.beauty.notification.service.SseEmitterService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class SseController {

    private final SseEmitterService sseEmitterService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper redisObjectMapper;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletResponse response) {
        // @LoginUserId 대신 직접 확인 — resolver 예외가 text/event-stream과 충돌 방지
        // 미인증 시 204: 브라우저 EventSource 스펙에서 재연결 방지
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || "anonymousUser".equals(auth.getName())) {
            log.warn("[SSE] 미인증 요청 — 204 반환 (재연결 방지)");
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return null;
        }
        Long userId = Long.parseLong(auth.getName());

        SseEmitter emitter = sseEmitterService.connect(userId);

        // 재연결 시 PEL(미ACK 메시지) 재전송
        flushPendingFromStream(userId, emitter);

        return emitter;
    }

    private void flushPendingFromStream(Long userId, SseEmitter emitter) {
        try {
            List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                    Consumer.from(RedisStreamConfig.NOTIFY_GROUP, RedisStreamConfig.NOTIFY_CONSUMER_NAME),
                    StreamOffset.create(RedisStreamConfig.NOTIFY_STREAM_KEY, ReadOffset.from("0"))
            );
            if (records == null || records.isEmpty()) return;

            for (MapRecord<String, Object, Object> record : records) {
                Object userIdObj = record.getValue().get("userId");
                if (!userId.toString().equals(String.valueOf(userIdObj))) continue;

                try {
                    Object payloadObj = record.getValue().get("payload");
                    NotificationMessage msg = redisObjectMapper.readValue(
                            String.valueOf(payloadObj), NotificationMessage.class);
                    msg.setStreamMessageId(record.getId().getValue());

                    emitter.send(SseEmitter.event().name("notification").data(msg, MediaType.APPLICATION_JSON));
                    stringRedisTemplate.opsForStream().acknowledge(
                            RedisStreamConfig.NOTIFY_STREAM_KEY,
                            RedisStreamConfig.NOTIFY_GROUP,
                            record.getId()
                    );
                    log.info("[SSE] PEL 재전송 및 ACK - userId: {}, recordId: {}", userId, record.getId());
                } catch (Exception e) {
                    log.warn("[SSE] PEL 재전송 실패 - recordId: {}", record.getId(), e);
                }
            }
        } catch (Exception e) {
            log.warn("[SSE] PEL 조회 실패 - userId: {}", userId, e);
        }
    }
}
