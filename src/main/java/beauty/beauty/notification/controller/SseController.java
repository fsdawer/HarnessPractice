package beauty.beauty.notification.controller;

import beauty.beauty.notification.dto.NotificationMessage;
import beauty.beauty.notification.service.NotificationService;
import beauty.beauty.notification.service.SseEmitterService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class SseController {

    private final SseEmitterService    sseEmitterService;
    private final NotificationService  notificationService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletResponse response) throws IOException {
        // [인증] @LoginUserId 대신 직접 확인 — resolver에서 예외를 던지면
        // GlobalExceptionHandler가 JSON으로 응답하려다 produces=text/event-stream과
        // 충돌해 HttpMediaTypeNotAcceptableException 캐스케이드가 발생한다.
        //
        // 204 No Content를 반환하는 이유:
        //   - 200(스트림 즉시 종료): 브라우저 EventSource 스펙에서 3초 후 자동 재연결
        //   - 204: 스펙상 "재연결하지 않음" → 무한 루프 방지
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || "anonymousUser".equals(auth.getName())) {
            log.warn("[SSE] 미인증 요청 — 204 반환 (재연결 방지)");
            response.setStatus(HttpServletResponse.SC_NO_CONTENT); // 204
            return null;
        }
        Long userId = Long.parseLong(auth.getName());

        SseEmitter emitter = sseEmitterService.connect(userId);

        // 연결 직후 미전달 알림 flush (Toss 결제 중 끊겼을 때 보관된 알림 전달)
        List<NotificationMessage> pending = notificationService.flushPending(userId);
        for (NotificationMessage msg : pending) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(msg, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                log.warn("[SSE] pending flush 중 오류 - userId: {}", userId, e);
                break;
            }
        }

        return emitter;
    }
}
