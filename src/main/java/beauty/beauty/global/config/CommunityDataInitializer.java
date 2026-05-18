package beauty.beauty.global.config;

import beauty.beauty.community.entity.Post;
import beauty.beauty.community.repository.PostRepository;
import beauty.beauty.coupon.entity.Coupon;
import beauty.beauty.coupon.entity.UserCoupon;
import beauty.beauty.coupon.repository.CouponRepository;
import beauty.beauty.coupon.repository.UserCouponRepository;
import beauty.beauty.user.entity.User;
import beauty.beauty.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CommunityDataInitializer implements ApplicationRunner {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    private static final String[] DISTRICTS = {
        "강남구", "서초구", "송파구", "마포구", "용산구",
        "성동구", "광진구", "중구", "종로구", "은평구"
    };

    private static final String[][] SAMPLES = {
        {"펌 후기 공유해요", "오늘 드디어 염색+펌 같이 했는데 생각보다 훨씬 잘 나왔어요!", null},
        {"강남 미용실 추천해주세요", "이사 오고 처음인데 가격대 괜찮고 실력 좋은 곳 알려주세요.", null},
        {"단발 컷 잘하는 곳 어디예요?", "앞머리 포함 단발로 자르려는데 어디서 해야 할지 모르겠어요.", null},
        {"염색 견적 받아봤어요", "근처 세 곳 돌아다니며 견적 받았는데 차이가 꽤 나더라고요.", "80000"},
        {"파마 오래 가는 방법 있나요", "항상 파마를 해도 한 달 만에 풀려서 고민입니다.", null},
        {"셀프 컬러 vs 미용실 비교", "셀프로 해봤다가 결국 미용실 가서 교정받은 후기입니다.", "50000"},
        {"탈색 후 관리 어떻게 하세요?", "탈색 후 머리카락이 너무 상해서 관리법 공유해요.", null},
        {"웨이브펌 시술 시간 얼마나 걸려요?", "처음 받으려는데 시간이 얼마나 걸리는지 궁금해요.", null},
        {"남자도 꼭 예약해야 하나요?", "동네 미용실인데 당일 방문 가능한지 궁금합니다.", null},
        {"가격 대비 만족했던 곳 추천", "15만원대에 전체 염색+컷 해줬는데 만족했어요.", "150000"},
        {"매직 스트레이트 후기", "곱슬이라 오래 고민했는데 결국 했어요. 결과 공유합니다!", "120000"},
        {"레이어드 컷 잘 어울리는 얼굴형은?", "미용사가 추천해준 스타일인데 의견 듣고 싶어요.", null},
        {"아이롱펌 유지 기간", "아이롱펌 하신 분들 보통 얼마나 유지되던가요?", null},
        {"헤어 에센스 뭐 쓰세요?", "미용사가 추천해준 거랑 인터넷 후기가 달라서 헷갈려요.", null},
        {"두피 스케일링 해봤어요", "두피 관리 서비스 받아봤는데 생각보다 시원하고 좋았어요.", "40000"},
        {"숏컷 도전기", "30년 만에 처음으로 숏컷 했는데 완전 만족!!", null},
        {"옴브레 염색 견적", "옴브레 하려는데 가격이 천차만별이라 평균이 궁금해요.", "130000"},
        {"미용학원 다니시는 분 계세요?", "여기서 미용 배우시는 분 있으면 팁 공유해 주세요.", null},
        {"헤어 클리닉 받고 나서", "손상모 집중 트리트먼트 받았는데 효과 느껴져요.", "60000"},
        {"펌 종류가 너무 많아요", "원장님이 권유하는 종류가 너무 많아서 결국 검색해봤어요.", null},
        {"환불 받을 수 있나요?", "결과물이 요청한 것과 너무 달라서 어떻게 해야 할지 모르겠어요.", null},
        {"단골 미용실 옮기기 망설여져요", "10년 다닌 곳인데 실력이 예전 같지 않아서 고민돼요.", null},
        {"네이버 예약 vs 직접 전화", "네이버 예약으로 하면 할인 있는 곳도 있더라고요.", null},
        {"새치 염색 주기 어떻게 되세요?", "새치가 빨리 올라와서 한 달에 한 번은 해야 해요.", "70000"},
        {"드라이 잘 해주는 곳 찾아요", "컷보다 드라이 마무리 퀄리티가 더 중요한 것 같아요.", null},
        {"모발 굵어지는 방법 있나요?", "선천적으로 모발이 가늘어서 볼륨감이 없어요.", null},
        {"미용실 대기 시간 줄이는 꿀팁", "주말에는 오전 첫 타임에 가야 기다리지 않더라고요.", null},
        {"남편한테 미용실 선물 어떤 거 좋아요?", "헤어 케어 패키지나 트리트먼트 선물 고민 중이에요.", null},
        {"잡지 속 스타일 참고해도 될까요?", "핀터레스트 사진 보여주면 비슷하게 해주더라고요.", null},
        {"미용실 냄새 빠지는 방법", "펌 후에 냄새가 오래 가는데 빨리 없애는 방법 있나요?", null},
        {"겨울 모발 관리법", "건조한 계절에 정전기가 너무 심해요.", null},
        {"커트 가격 적당한 건지 모르겠어요", "남자 커트 2만원대면 괜찮은 건가요?", "20000"},
        {"원장님과 직원 실력 차이", "예약할 때 원장님 지명하는 게 맞는지 항상 고민돼요.", null},
        {"뿌리 염색만 할 수 있어요?", "전체 다 하기엔 부담이 돼서 뿌리만 가능한지 궁금해요.", "35000"},
        {"볼드모트 펌 들어보셨어요?", "SNS에서 봤는데 어디서 받을 수 있는지 모르겠어요.", null},
        {"헤어 팩 집에서 만드는 법", "달걀+올리브오일로 해봤는데 효과 있더라고요.", null},
        {"샴푸 어떤 거 쓰세요?", "미용사 추천 샴푸랑 마트 샴푸 차이 체감하시나요?", null},
        {"헤어 롤 vs 고데기", "롤로 하면 더 자연스럽다는데 연습이 많이 필요하더라고요.", null},
        {"염색 후 샴푸 언제 해요?", "보통 48시간 기다리는 게 맞나요?", null},
        {"C컬 vs S컬 뭐가 더 자연스러워요?", "처음 펌 도전인데 어느 쪽이 관리가 쉬운지 궁금해요.", null},
        {"머리 빨리 기르는 방법", "미용사가 두피 마사지 추천해줬는데 정말 효과 있나요?", null},
        {"앞머리 셀프로 자르다가 망했어요", "다음에 미용실 갈 때까지 가릴 방법 알려주세요.", null},
        {"헤어 왁스 vs 스프레이 어떤 게 좋아요?", "요즘은 크림 타입이 대세인 것 같던데요.", null},
        {"방문 서비스 받아보신 분?", "어르신 부모님 위해 방문 서비스 알아보고 있어요.", null},
        {"미용실 후기 플랫폼 어디 쓰세요?", "네이버 vs 카카오 vs 앱 중 어디가 후기 제일 많나요?", null},
        {"신규 미용실 오픈 할인", "우리 동네에 새로 생긴 곳 오픈 이벤트 중이에요!", "50000"},
        {"모발 교정 샴푸 써보셨나요?", "산성 샴푸 쓰면 염색 오래 간다는데 사실인가요?", null},
        {"겨울 헤어 추천 스타일", "차가운 느낌보다 따뜻한 느낌 스타일 추천해 주세요.", null},
        {"셀프 펌 도전기", "유튜브 보고 직접 해봤는데 반은 성공 반은 실패ㅎㅎ", "0"},
        {"미용실에서 찍은 사진 공유해요", "오늘 완성된 스타일 공유! 드디어 원하던 스타일 완성!", null}
    };

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (postRepository.count() > 0) return;

        List<User> users = userRepository.findAll();
        if (users.isEmpty()) return;

        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < SAMPLES.length; i++) {
            User author = users.get(i % users.size());
            String district = DISTRICTS[i % DISTRICTS.length];
            Integer price = SAMPLES[i][2] != null ? Integer.parseInt(SAMPLES[i][2]) : null;

            posts.add(Post.builder()
                    .user(author)
                    .title(SAMPLES[i][0])
                    .content(SAMPLES[i][1])
                    .district(district)
                    .price(price)
                    .viewCount(i * 3 + 10)
                    .likeCount(i % 7)
                    .createdAt(LocalDateTime.now().minusDays(SAMPLES.length - i))
                    .build());
        }
        postRepository.saveAll(posts);

        // 샘플 쿠폰 — 최초 실행 시에만 생성
        if (couponRepository.count() == 0) {
            Coupon coupon = couponRepository.save(Coupon.builder()
                    .code("WELCOME10")
                    .name("신규 가입 10% 할인 쿠폰")
                    .discountRate(10)
                    .minPrice(10000)
                    .maxDiscount(5000)
                    .startAt(LocalDateTime.now().minusDays(1))
                    .expiredAt(LocalDateTime.now().plusYears(1))
                    .build());

            // 모든 유저에게 발급
            List<UserCoupon> userCoupons = users.stream()
                    .map(u -> UserCoupon.builder().user(u).coupon(coupon).build())
                    .toList();
            userCouponRepository.saveAll(userCoupons);
        }
    }
}
