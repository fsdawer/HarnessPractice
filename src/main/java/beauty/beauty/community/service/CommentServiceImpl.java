package beauty.beauty.community.service;

import beauty.beauty.community.dto.CommentRequest;
import beauty.beauty.community.dto.CommentResponse;
import beauty.beauty.community.entity.Comment;
import beauty.beauty.community.repository.CommentRepository;
import beauty.beauty.community.repository.PostRepository;
import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.user.entity.User;
import beauty.beauty.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CommentResponse addComment(Long userId, Long postId, CommentRequest request) {
        var post = postRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .content(request.getContent())
                .build();
        return CommentResponse.from(commentRepository.save(comment));
    }

    @Override
    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        comment.softDelete();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(Long postId) {
        return commentRepository.findByPostIdWithUser(postId)
                .stream()
                .map(CommentResponse::from)
                .toList();
    }
}
