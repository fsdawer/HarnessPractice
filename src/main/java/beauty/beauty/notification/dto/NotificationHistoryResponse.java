package beauty.beauty.notification.dto;

import beauty.beauty.notification.entity.NotificationHistory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationHistoryResponse {
    private Long id;
    private String type;
    private String message;
    private boolean isRead;
    private LocalDateTime createdAt;

    public static NotificationHistoryResponse from(NotificationHistory h) {
        return NotificationHistoryResponse.builder()
                .id(h.getId())
                .type(h.getType())
                .message(h.getMessage())
                .isRead(h.isRead())
                .createdAt(h.getCreatedAt())
                .build();
    }
}
