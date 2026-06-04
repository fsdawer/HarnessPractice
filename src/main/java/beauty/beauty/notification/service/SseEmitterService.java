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
            log.info("[SSE] 기존 연결 교체 - userId: {}", userId);
        }
        emitter.onCompletion(() -> {
            emitters.remove(userId, emitter);
            log.info("[SSE] 연결 종료(completion) - userId: {}", userId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(userId, emitter);
            log.info("[SSE] 연결 타임아웃 - userId: {}", userId);
        });
        emitter.onError(e -> {
            emitters.remove(userId, emitter);
            log.warn("[SSE] 연결 오류 - userId: {}, error: {}", userId, e.getMessage());
        });
        log.info("[SSE] 연결 등록 완료 - userId: {}, 현재 연결 수: {}", userId, emitters.size());
        return emitter;
    }

    /** 전송 성공 시 true, 연결 없거나 실패 시 false */
    public boolean send(Long userId, NotificationMessage msg) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            log.info("[SSE] 전송 대상 없음(미연결) - userId: {}, type: {}", userId, msg.getType());
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name("notification").data(msg, MediaType.APPLICATION_JSON));
            log.info("[SSE] 전송 성공 - userId: {}, type: {}", userId, msg.getType());
            return true;
        } catch (Exception e) {
            emitters.remove(userId, emitter);
            log.warn("[SSE] 전송 실패로 emitter 제거 - userId: {}, type: {}, error: {}", userId, msg.getType(), e.getMessage());
            return false;
        }
    }
}
