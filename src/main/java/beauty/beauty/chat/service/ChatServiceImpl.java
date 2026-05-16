package beauty.beauty.chat.service;

import beauty.beauty.chat.dto.ChatRoomResponse;
import beauty.beauty.chat.dto.MessageResponse;
import beauty.beauty.chat.dto.SendMessageRequest;
import beauty.beauty.chat.entity.ChatRoom;
import beauty.beauty.chat.entity.Message;
import beauty.beauty.chat.repository.ChatRoomRepository;
import beauty.beauty.chat.repository.MessageRepository;
import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.reservation.entity.Reservation;
import beauty.beauty.stylist.repository.StylistProfileRepository;
import beauty.beauty.user.entity.User;
import beauty.beauty.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final StylistProfileRepository stylistProfileRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper redisObjectMapper;
    private final ChatReadService chatReadService;

    @Override
    @Transactional
    public ChatRoom createRoomForReservation(Reservation reservation) {
        return chatRoomRepository.findByReservationId(reservation.getId())
                .orElseGet(() -> chatRoomRepository.save(
                        ChatRoom.builder()
                                .reservation(reservation)
                                .user(reservation.getUser())
                                .stylistUser(reservation.getStylistProfile().getUser())
                                .build()
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public ChatRoomResponse getRoom(Long userId, Long roomId) {
        ChatRoom room = findRoomOrThrow(roomId);
        checkAccess(userId, room);
        return ChatRoomResponse.from(room, getSalonName(room.getStylistUser().getId()));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "chat_rooms", key = "#userId")
    public List<ChatRoomResponse> getMyRooms(Long userId) {
        List<ChatRoom> asUser    = chatRoomRepository.findByUserIdOrderByLastMessageAtDesc(userId);
        List<ChatRoom> asStylist = chatRoomRepository.findByStylistUserIdOrderByLastMessageAtDesc(userId);

        List<ChatRoom> all = Stream.concat(asUser.stream(), asStylist.stream())
                .sorted(Comparator.comparing(ChatRoom::getLastMessageAt).reversed())
                .toList();

        if (all.isEmpty()) return List.of();

        Set<Long> stylistUserIds = all.stream()
                .map(r -> r.getStylistUser().getId())
                .collect(Collectors.toSet());

        Map<Long, String> salonNameMap = stylistProfileRepository
                .findSalonNamesByUserIds(stylistUserIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long)   row[0],
                        row -> (String) row[1],
                        (a, b) -> a
                ));

        return all.stream()
                .map(room -> ChatRoomResponse.from(
                        room,
                        salonNameMap.get(room.getStylistUser().getId()),
                        chatReadService.getUnreadCount(room.getId(), userId)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(Long userId, Long roomId) {
        ChatRoom room = findRoomOrThrow(roomId);
        checkAccess(userId, room);
        return messageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId)
                .stream().map(MessageResponse::from).toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = "chat_rooms", allEntries = true)
    public MessageResponse sendMessage(Long userId, SendMessageRequest request) {
        ChatRoom room = findRoomOrThrow(request.getRoomId());
        checkAccess(userId, room);

        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Message saved = messageRepository.save(Message.builder()
                .chatRoom(room)
                .sender(sender)
                .content(request.getContent())
                .imageUrl(request.getImageUrl())
                .build());

        room.setLastMessageAt(LocalDateTime.now());
        room.setLastMessageContent(request.getContent() != null ? request.getContent() : "[사진]");

        MessageResponse response = MessageResponse.from(saved);
        publishToRedis(request.getRoomId(), response);
        return response;
    }

    @Override
    @Transactional
    public void markAsRead(Long userId, Long roomId) {
        ChatRoom room = findRoomOrThrow(roomId);
        checkAccess(userId, room);
        // Redis에 lastReadId 저장 (O(1)) — DB UPDATE는 제거해 쓰기 부하 감소
        chatReadService.markAsRead(roomId, userId);
    }

    private void publishToRedis(Long roomId, MessageResponse response) {
        try {
            String json = redisObjectMapper.writeValueAsString(response);
            stringRedisTemplate.convertAndSend("chat:room:" + roomId, json);
        } catch (JacksonException e) {
            log.error("채팅 메시지 Redis 발행 오류 roomId={}", roomId, e);
        }
    }

    private String getSalonName(Long stylistUserId) {
        return stylistProfileRepository.findByUserId(stylistUserId)
                .map(p -> p.getSalon() != null ? p.getSalon().getName() : null)
                .orElse(null);
    }

    private ChatRoom findRoomOrThrow(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    private void checkAccess(Long userId, ChatRoom room) {
        boolean isUser    = room.getUser().getId().equals(userId);
        boolean isStylist = room.getStylistUser().getId().equals(userId);
        if (!isUser && !isStylist) {
            throw new CustomException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }
    }
}
