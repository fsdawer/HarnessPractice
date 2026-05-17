package beauty.beauty.community.service;

import beauty.beauty.community.dto.PostCreateRequest;
import beauty.beauty.community.dto.PostResponse;
import beauty.beauty.community.entity.Post;
import beauty.beauty.community.entity.PostLike;
import beauty.beauty.community.repository.PostLikeRepository;
import beauty.beauty.community.repository.PostRepository;
import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.reservation.entity.Reservation;
import beauty.beauty.reservation.repository.ReservationRepository;
import beauty.beauty.user.entity.User;
import beauty.beauty.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private static final int PAGE_SIZE = 20;

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;

    // [Flow 1] 글쓰기: reservationId 있으면 본인 예약인지 검증
    @Override
    @Transactional
    public PostResponse createPost(Long userId, PostCreateRequest req) {
        User user = findUserById(userId);

        Reservation reservation = null;
        if (req.getReservationId() != null) {
            reservation = reservationRepository.findById(req.getReservationId())
                .orElseThrow(() -> new CustomException(ErrorCode.RESERVATION_NOT_FOUND));
            if (!reservation.getUser().getId().equals(userId)) {
                throw new CustomException(ErrorCode.RESERVATION_NOT_MINE);
            }
        }

        Post post = Post.builder()
            .user(user)
            .reservation(reservation)
            .title(req.getTitle())
            .content(req.getContent())
            .district(req.getDistrict())
            .price(req.getPrice())
            .build();

        return PostResponse.from(postRepository.save(post), false);
    }

    // [Flow 2] 전체 탭 목록: 비인증이므로 likedByMe = false
    @Override
    @Transactional(readOnly = true)
    public List<PostResponse> getPosts(Long lastId) {
        return postRepository.findAllCursor(lastId, PAGE_SIZE).stream()
            .map(p -> PostResponse.from(p, false))
            .toList();
    }

    // [Flow 3] 우리동네 탭: verifiedDistrict 없으면 예외
    @Override
    @Transactional(readOnly = true)
    public List<PostResponse> getLocalPosts(Long userId, Long lastId) {
        User user = findUserById(userId);
        if (user.getVerifiedDistrict() == null) {
            throw new CustomException(ErrorCode.DISTRICT_NOT_VERIFIED);
        }
        return postRepository.findByDistrictCursor(user.getVerifiedDistrict(), lastId, PAGE_SIZE).stream()
            .map(p -> PostResponse.from(p, false))
            .toList();
    }

    // [Flow 4] 게시글 상세: 조회수 +1, likedByMe 계산
    @Override
    @Transactional
    public PostResponse getPost(Long postId, Long userId) {
        Post post = findPostById(postId);
        post.setViewCount(post.getViewCount() + 1);

        boolean likedByMe = userId != null && postLikeRepository.existsByUserIdAndPostId(userId, postId);
        return PostResponse.from(post, likedByMe);
    }

    // [Flow 5] 게시글 삭제: 본인 글인지 검증 후 soft delete
    @Override
    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = findPostById(postId);
        if (!post.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_MY_POST);
        }
        post.setDeletedAt(LocalDateTime.now());
    }

    // [Flow 6] 좋아요 토글: 있으면 삭제+count--, 없으면 저장+count++
    @Override
    @Transactional
    public void toggleLike(Long userId, Long postId) {
        Post post = findPostById(postId);
        User user = findUserById(userId);

        if (postLikeRepository.existsByUserIdAndPostId(userId, postId)) {
            postLikeRepository.deleteByUserIdAndPostId(userId, postId);
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
        } else {
            try {
                postLikeRepository.save(PostLike.builder().user(user).post(post).build());
                post.setLikeCount(post.getLikeCount() + 1);
            } catch (DataIntegrityViolationException ignored) {
                // 동시 요청으로 중복 삽입 시 무시
            }
        }
    }

    private Post findPostById(Long postId) {
        return postRepository.findById(postId)
            .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
