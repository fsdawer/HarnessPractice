package beauty.beauty.notification.controller;

import beauty.beauty.global.annotation.LoginUserId;
import beauty.beauty.notification.dto.NotificationHistoryResponse;
import beauty.beauty.notification.entity.NotificationHistory;
import beauty.beauty.notification.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationHistoryController {

    private final NotificationHistoryRepository notificationHistoryRepository;

    @GetMapping
    public ResponseEntity<List<NotificationHistoryResponse>> getList(@LoginUserId Long userId) {
        return ResponseEntity.ok(
                notificationHistoryRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId)
                        .stream()
                        .map(NotificationHistoryResponse::from)
                        .toList()
        );
    }

    @PatchMapping("/{id}/read")
    @Transactional
    public ResponseEntity<Void> markRead(@LoginUserId Long userId, @PathVariable Long id) {
        notificationHistoryRepository.findById(id).ifPresent(n -> {
            if (n.getUserId().equals(userId)) n.markRead();
        });
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/read-all")
    @Transactional
    public ResponseEntity<Void> markAllRead(@LoginUserId Long userId) {
        notificationHistoryRepository.markAllReadByUserId(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@LoginUserId Long userId) {
        long count = notificationHistoryRepository.countByUserIdAndIsRead(userId, false);
        return ResponseEntity.ok(Map.of("count", count));
    }
}
