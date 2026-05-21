package beauty.beauty.notification.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {
    private String type;
    private Long reservationId;
    private String stylistName;
    private String clientName;
    private String reservedAt;
    private String message;
    private String dedupKey;       // 클라이언트 중복 수신 방지용 (type:reservationId 또는 type:reservedAt)
}
