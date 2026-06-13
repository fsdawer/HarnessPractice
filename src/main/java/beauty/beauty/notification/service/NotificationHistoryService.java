package beauty.beauty.notification.service;

import beauty.beauty.notification.dto.NotificationHistoryResponse;
import beauty.beauty.notification.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationHistoryService {

    private final NotificationHistoryRepository notificationHistoryRepository;

    @Transactional(readOnly = true)
    public List<NotificationHistoryResponse> getList(Long userId) {
        return notificationHistoryRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationHistoryResponse::from)
                .toList();
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        notificationHistoryRepository.findById(id).ifPresent(n -> {
            if (n.getUserId().equals(userId)) n.markRead();
        });
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationHistoryRepository.markAllReadByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationHistoryRepository.countByUserIdAndIsRead(userId, false);
    }
}
