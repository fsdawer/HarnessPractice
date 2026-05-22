package beauty.beauty.community.dto;

import beauty.beauty.community.entity.Comment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CommentResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String userProfileImg;
    private String content;
    private LocalDateTime createdAt;

    public static CommentResponse from(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .userId(comment.getUser().getId())
                .userName(comment.getUser().getName())
                .userProfileImg(comment.getUser().getProfileImg())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
