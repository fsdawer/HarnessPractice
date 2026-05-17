package beauty.beauty.community.controller;

import beauty.beauty.community.dto.PostCreateRequest;
import beauty.beauty.community.dto.PostResponse;
import beauty.beauty.community.service.PostService;
import beauty.beauty.global.annotation.LoginUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    // GET /api/community?lastId=       전체 탭 (비인증 허용)
    @GetMapping
    public ResponseEntity<List<PostResponse>> getPosts(
            @RequestParam(required = false) Long lastId) {
        return ResponseEntity.ok(postService.getPosts(lastId));
    }

    // GET /api/community/local?lastId= 우리동네 탭 (인증 필수)
    @GetMapping("/local")
    public ResponseEntity<List<PostResponse>> getLocalPosts(
            @LoginUserId Long userId,
            @RequestParam(required = false) Long lastId) {
        return ResponseEntity.ok(postService.getLocalPosts(userId, lastId));
    }

    // GET /api/community/{id}          게시글 상세 (비인증 허용, 로그인 시 likedByMe 계산)
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPost(@PathVariable Long id) {
        Long userId = resolveOptionalUserId();
        return ResponseEntity.ok(postService.getPost(id, userId));
    }

    // POST /api/community              글쓰기
    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @LoginUserId Long userId,
            @Valid @RequestBody PostCreateRequest req) {
        return ResponseEntity.ok(postService.createPost(userId, req));
    }

    // DELETE /api/community/{id}       게시글 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            @LoginUserId Long userId,
            @PathVariable Long id) {
        postService.deletePost(userId, id);
        return ResponseEntity.noContent().build();
    }

    // POST /api/community/{id}/like    좋아요 토글
    @PostMapping("/{id}/like")
    public ResponseEntity<Void> toggleLike(
            @LoginUserId Long userId,
            @PathVariable Long id) {
        postService.toggleLike(userId, id);
        return ResponseEntity.ok().build();
    }

    // [흐름] SecurityContext에서 인증 여부 확인 후 userId 반환 (비인증이면 null)
    private Long resolveOptionalUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
