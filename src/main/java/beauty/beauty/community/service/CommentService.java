package beauty.beauty.community.service;

import beauty.beauty.community.dto.CommentRequest;
import beauty.beauty.community.dto.CommentResponse;

import java.util.List;

public interface CommentService {
    CommentResponse addComment(Long userId, Long postId, CommentRequest request);
    void deleteComment(Long userId, Long commentId);
    List<CommentResponse> getComments(Long postId);
}
