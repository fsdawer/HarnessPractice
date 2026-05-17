package beauty.beauty.community.service;

import beauty.beauty.community.dto.PostCreateRequest;
import beauty.beauty.community.dto.PostResponse;

import java.util.List;

public interface PostService {

    // POST /api/community              글쓰기
    PostResponse createPost(Long userId, PostCreateRequest req);

    // GET  /api/community              전체 탭 (비인증 가능)
    List<PostResponse> getPosts(Long lastId);

    // GET  /api/community/local        우리동네 탭 (인증된 구 기반)
    List<PostResponse> getLocalPosts(Long userId, Long lastId);

    // GET  /api/community/{id}         게시글 상세
    PostResponse getPost(Long postId, Long userId);

    // DELETE /api/community/{id}       게시글 삭제
    void deletePost(Long userId, Long postId);

    // POST /api/community/{id}/like    좋아요 토글
    void toggleLike(Long userId, Long postId);
}
