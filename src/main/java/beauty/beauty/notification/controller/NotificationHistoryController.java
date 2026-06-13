package beauty.beauty.notification.controller;

import beauty.beauty.global.annotation.LoginUserId;
import beauty.beauty.notification.dto.NotificationHistoryResponse;
import beauty.beauty.notification.service.NotificationHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationHistoryController {

    private final NotificationHistoryService notificationHistoryService;

    @GetMapping
    public ResponseEntity<List<NotificationHistoryResponse>> getList(@LoginUserId Long userId) {
        return ResponseEntity.ok(notificationHistoryService.getList(userId));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@LoginUserId Long userId, @PathVariable Long id) {
        notificationHistoryService.markRead(userId, id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@LoginUserId Long userId) {
        notificationHistoryService.markAllRead(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@LoginUserId Long userId) {
        return ResponseEntity.ok(Map.of("count", notificationHistoryService.getUnreadCount(userId)));
    }
}
