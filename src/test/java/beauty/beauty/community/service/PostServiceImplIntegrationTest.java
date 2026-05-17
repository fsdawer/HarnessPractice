package beauty.beauty.community.service;

import beauty.beauty.community.dto.PostCreateRequest;
import beauty.beauty.community.dto.PostResponse;
import beauty.beauty.community.entity.Post;
import beauty.beauty.community.entity.PostLike;
import beauty.beauty.community.repository.PostLikeRepository;
import beauty.beauty.community.repository.PostRepository;
import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.notification.service.NotificationService;
import beauty.beauty.ranking.service.RankingService;
import beauty.beauty.reservation.entity.Reservation;
import beauty.beauty.reservation.repository.ReservationRepository;
import beauty.beauty.stylist.entity.StylistProfile;
import beauty.beauty.stylist.entity.StylistServiceItem;
import jakarta.persistence.EntityManager;
import beauty.beauty.stylist.repository.StylistProfileRepository;
import beauty.beauty.stylist.repository.StylistServiceRepository;
import beauty.beauty.user.entity.User;
import beauty.beauty.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostServiceImplIntegrationTest {

    @Autowired
    private PostService postService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private StylistProfileRepository stylistProfileRepository;

    @Autowired
    private StylistServiceRepository stylistServiceRepository;

    @MockitoBean
    private RankingService rankingService;

    @MockitoBean
    private NotificationService notificationService;

    private User user;
    private User otherUser;
    private StylistProfile stylistProfile;
    private StylistServiceItem serviceItem;

    @BeforeEach
    void setUp() {
        long ts = System.nanoTime();

        user = userRepository.save(User.builder()
                .username("user_" + ts)
                .email("user_" + ts + "@test.com")
                .name("테스트유저")
                .password("pw")
                .build());

        otherUser = userRepository.save(User.builder()
                .username("other_" + ts)
                .email("other_" + ts + "@test.com")
                .name("타인유저")
                .password("pw")
                .build());

        User stylistUser = userRepository.save(User.builder()
                .username("stylist_" + ts)
                .email("stylist_" + ts + "@test.com")
                .name("미용사")
                .password("pw")
                .role(User.Role.STYLIST)
                .build());

        // salon은 nullable이므로 생략 (SALONS 테이블이 H2 POINT 타입 문제로 생성 불가)
        stylistProfile = stylistProfileRepository.save(StylistProfile.builder()
                .user(stylistUser)
                .salon(null)
                .experience(3)
                .build());

        serviceItem = stylistServiceRepository.save(StylistServiceItem.builder()
                .stylistProfile(stylistProfile)
                .name("커트")
                .price(15000)
                .duration(30)
                .build());
    }

    // ── createPost ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("예약 연결 없이 글 작성 시 게시글이 저장된다")
    void createPost_withoutReservation_saved() {
        // given
        PostCreateRequest req = buildRequest("제목", "내용", "강남구", null, null);

        // when
        PostResponse response = postService.createPost(user.getId(), req);

        // then
        assertThat(response.getId()).isNotNull();
        assertThat(response.getTitle()).isEqualTo("제목");
        assertThat(response.getDistrict()).isEqualTo("강남구");
        assertThat(response.getReservationSummary()).isNull();
    }

    @Test
    @DisplayName("본인 예약을 연결해서 글 작성 시 reservationSummary가 포함된다")
    void createPost_withMyReservation_reservationSummaryIncluded() {
        // given
        Reservation myReservation = saveReservation(user);
        PostCreateRequest req = buildRequest("후기", "좋았어요", "마포구", null, myReservation.getId());

        // when
        PostResponse response = postService.createPost(user.getId(), req);

        // then
        assertThat(response.getReservationSummary()).isNotNull();
        assertThat(response.getReservationSummary().getReservationId()).isEqualTo(myReservation.getId());
    }

    @Test
    @DisplayName("타인 예약 연결 시 RESERVATION_NOT_MINE 예외가 발생한다")
    void createPost_withOtherReservation_throwsReservationNotMine() {
        // given
        Reservation otherReservation = saveReservation(otherUser);
        PostCreateRequest req = buildRequest("제목", "내용", "강남구", null, otherReservation.getId());

        // when / then
        assertThatThrownBy(() -> postService.createPost(user.getId(), req))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESERVATION_NOT_MINE));
    }

    // ── getLocalPosts ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("verifiedDistrict 없는 유저는 DISTRICT_NOT_VERIFIED 예외가 발생한다")
    void getLocalPosts_noVerifiedDistrict_throwsDistrictNotVerified() {
        // user.verifiedDistrict is null by default
        assertThatThrownBy(() -> postService.getLocalPosts(user.getId(), null))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DISTRICT_NOT_VERIFIED));
    }

    @Test
    @DisplayName("verifiedDistrict 있는 유저는 해당 구 게시글만 반환된다")
    void getLocalPosts_withVerifiedDistrict_returnsOnlyMatchingDistrict() {
        // given
        user.setVerifiedDistrict("강남구");
        userRepository.save(user);

        postRepository.save(buildPost(user, "강남구"));
        postRepository.save(buildPost(user, "강남구"));
        postRepository.save(buildPost(otherUser, "마포구")); // 다른 구

        // when
        List<PostResponse> result = postService.getLocalPosts(user.getId(), null);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(p -> p.getDistrict().equals("강남구"));
    }

    // ── getPosts ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("전체 게시글 커서 기반 조회 시 최대 20개가 반환된다")
    void getPosts_cursorBased_returns20() {
        // given: 25개 저장
        for (int i = 0; i < 25; i++) {
            postRepository.save(buildPost(user, "강남구"));
        }

        // when
        List<PostResponse> result = postService.getPosts(null);

        // then
        assertThat(result).hasSize(20);
    }

    // ── getPost ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("게시글 조회 시 viewCount가 1 증가한다")
    void getPost_viewCountIncreased() {
        // given
        Post post = postRepository.save(buildPost(user, "강남구"));
        int initialViewCount = post.getViewCount();

        // when
        PostResponse response = postService.getPost(post.getId(), null);

        // then
        assertThat(response.getViewCount()).isEqualTo(initialViewCount + 1);
    }

    @Test
    @DisplayName("없는 postId 조회 시 POST_NOT_FOUND 예외가 발생한다")
    void getPost_notFound_throwsPostNotFound() {
        assertThatThrownBy(() -> postService.getPost(999999L, null))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.POST_NOT_FOUND));
    }

    // ── deletePost ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("본인 글 삭제 시 deletedAt이 설정된다 (soft delete)")
    void deletePost_mine_softDeleted() {
        // given
        Post post = postRepository.save(buildPost(user, "강남구"));
        Long postId = post.getId();

        // when
        postService.deletePost(user.getId(), postId);

        // then: L1 캐시를 비워 @SQLRestriction 적용 확인
        entityManager.flush();
        entityManager.clear();
        assertThat(postRepository.findById(postId)).isEmpty();
    }

    @Test
    @DisplayName("타인 글 삭제 시 NOT_MY_POST 예외가 발생한다")
    void deletePost_notMine_throwsNotMyPost() {
        // given
        Post post = postRepository.save(buildPost(user, "강남구"));

        // when / then
        assertThatThrownBy(() -> postService.deletePost(otherUser.getId(), post.getId()))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_MY_POST));
    }

    // ── toggleLike ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("좋아요 추가 시 likeCount가 1 증가한다")
    void toggleLike_add_likeCountIncreased() {
        // given
        Post post = postRepository.save(buildPost(user, "강남구"));
        int initial = post.getLikeCount();

        // when
        postService.toggleLike(otherUser.getId(), post.getId());

        // then
        Post updated = postRepository.findById(post.getId()).orElseThrow();
        assertThat(updated.getLikeCount()).isEqualTo(initial + 1);
    }

    @Test
    @DisplayName("좋아요 취소 시 likeCount가 1 감소한다")
    void toggleLike_cancel_likeCountDecreased() {
        // given
        Post post = postRepository.save(buildPost(user, "강남구"));
        // 먼저 좋아요 추가
        postLikeRepository.save(PostLike.builder().user(otherUser).post(post).build());
        post.setLikeCount(1);
        postRepository.save(post);

        // when
        postService.toggleLike(otherUser.getId(), post.getId());

        // then
        Post updated = postRepository.findById(post.getId()).orElseThrow();
        assertThat(updated.getLikeCount()).isEqualTo(0);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PostCreateRequest buildRequest(String title, String content, String district,
                                           Integer price, Long reservationId) {
        try {
            var req = new PostCreateRequest();
            setField(req, "title", title);
            setField(req, "content", content);
            setField(req, "district", district);
            setField(req, "price", price);
            setField(req, "reservationId", reservationId);
            return req;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        var field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    private Post buildPost(User author, String district) {
        return Post.builder()
                .user(author)
                .title("테스트 제목")
                .content("테스트 내용")
                .district(district)
                .build();
    }

    private Reservation saveReservation(User owner) {
        return reservationRepository.save(Reservation.builder()
                .user(owner)
                .stylistProfile(stylistProfile)
                .service(serviceItem)
                .reservedAt(LocalDateTime.now().plusDays(1))
                .totalPrice(15000)
                .build());
    }
}
