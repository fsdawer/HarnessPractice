package beauty.beauty.chat.entity;

import beauty.beauty.reservation.entity.Reservation;
import beauty.beauty.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_rooms", indexes = {
    @Index(name = "idx_chatroom_user", columnList = "user_id, last_message_at DESC"),
    @Index(name = "idx_chatroom_stylist", columnList = "stylist_user_id, last_message_at DESC")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ChatRoom {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stylist_user_id", nullable = false)
    private User stylistUser;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // 메시지 전송 시마다 갱신 — 채팅방 목록 최신순 정렬에 사용
    @Builder.Default
    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt = LocalDateTime.now();

    // 목록 미리보기용 최근 메시지 내용
    @Builder.Default
    @Column(name = "last_message_content", length = 300)
    private String lastMessageContent = "";
}
