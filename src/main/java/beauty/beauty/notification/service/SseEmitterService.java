package beauty.beauty.notification.service;

import beauty.beauty.notification.dto.NotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseEmitterService {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        SseEmitter old = emitters.put(userId, emitter);
        if (old != null) {
            try { old.complete(); } catch (Exception ignored) {}
        }
        emitter.onCompletion(() -> emitters.remove(userId, emitter));
        emitter.onTimeout(()    -> emitters.remove(userId, emitter));
        emitter.onError(e      -> emitters.remove(userId, emitter));
        log.debug("[SSE] 연결 등록 - userId: {}", userId);
        return emitter;
    }

    /** 전송 성공 시 true, 연결 없거나 실패 시 false */
    public boolean send(Long userId, NotificationMessage msg) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) return false;
        try {
            emitter.send(SseEmitter.event().name("notification").data(msg, MediaType.APPLICATION_JSON));
            return true;
        } catch (Exception e) {
            emitters.remove(userId, emitter);
            log.debug("[SSE] 전송 실패로 emitter 제거 - userId: {}", userId);
            return false;
        }
    }
}
