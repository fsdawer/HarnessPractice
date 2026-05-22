package beauty.beauty.community.controller;

import beauty.beauty.community.dto.CommentRequest;
import beauty.beauty.community.dto.CommentResponse;
import beauty.beauty.community.service.CommentService;
import beauty.beauty.global.annotation.LoginUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable Long postId) {
        return ResponseEntity.ok(commentService.getComments(postId));
    }

    @PostMapping
    public ResponseEntity<CommentResponse> addComment(
            @LoginUserId Long userId,
            @PathVariable Long postId,
            @RequestBody CommentRequest request) {
        return ResponseEntity.ok(commentService.addComment(userId, postId, request));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @LoginUserId Long userId,
            @PathVariable Long postId,
            @PathVariable Long commentId) {
        commentService.deleteComment(userId, commentId);
        return ResponseEntity.ok().build();
    }
}
