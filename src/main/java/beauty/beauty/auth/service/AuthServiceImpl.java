package beauty.beauty.auth.service;

import beauty.beauty.auth.dto.LoginRequest;
import beauty.beauty.auth.dto.RegisterRequest;
import beauty.beauty.auth.dto.TokenResponse;
import beauty.beauty.coupon.entity.UserCoupon;
import beauty.beauty.coupon.repository.CouponRepository;
import beauty.beauty.coupon.repository.UserCouponRepository;
import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.global.jwt.JwtUtil;
import beauty.beauty.global.redis.TokenBlacklistService;
import beauty.beauty.stylist.entity.Salon;
import beauty.beauty.stylist.entity.StylistProfile;
import beauty.beauty.stylist.repository.SalonRepository;
import beauty.beauty.stylist.repository.StylistProfileRepository;
import beauty.beauty.user.entity.User;
import beauty.beauty.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final StylistProfileRepository stylistProfileRepository;
    private final SalonRepository salonRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public void register(RegisterRequest request) {
        // [Flow 1] 회원가입 검증: 아이디, 이메일 중복 체크 (DB 접근)
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new CustomException(ErrorCode.DUPLICATE_USERNAME);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        // [Flow 2] 비밀번호 암호화 및 User 엔티티 생성 후 DB 저장
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .phone(request.getPhone())
                .role(User.Role.valueOf(request.getRole()))
                .provider(User.Provider.LOCAL)
                .isVerified(true)
                .build();
        userRepository.save(user);

        // [Flow 3] USER로 가입 시 활성화된 쿠폰이 있으면 자동 발급
        if (User.Role.USER.equals(user.getRole())) {
            couponRepository.findFirstActive().ifPresent(coupon ->
                userCouponRepository.save(UserCoupon.builder().user(user).coupon(coupon).build())
            );
        }

        // [Flow 4] 미용사(STYLIST)로 가입하는 경우 추가 프로필(Salon, StylistProfile) 연동 저장
        if (User.Role.STYLIST.equals(user.getRole())) {
            Salon salon = salonRepository.save(Salon.builder()
                    .name(request.getSalonName())
                    .address(request.getLocation())
                    .phone(request.getSalonPhone())
                    .description(request.getSalonDescription())
                    .build());
            stylistProfileRepository.save(StylistProfile.builder()
                    .user(user)
                    .salon(salon)
                    .build());
        }
    }

    @Override
    public TokenResponse login(LoginRequest request) {
        // [Flow 1] DB에서 사용자 조회 및 비밀번호 매칭 (Spring Security PasswordEncoder 사용)
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        // [Flow 2] 로그인 성공 시 AccessToken 및 RefreshToken 발급
        String accessToken  = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        
        // [Flow 3] RefreshToken을 DB에 저장하여 추후 검증 및 탈취 방지에 활용
        user.setRefreshToken(refreshToken);

        return new TokenResponse(accessToken, refreshToken, user.getRole().name());
    }

    @Override
    public TokenResponse refresh(String refreshToken) {
        // [Flow 1] 클라이언트가 보낸 RefreshToken의 서명 및 만료일자 검증
        if (!jwtUtil.isValid(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        // [Flow 2] 토큰에서 추출한 ID로 DB 사용자 조회
        Long userId = jwtUtil.getUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // [Flow 3] DB에 저장된 실제 RefreshToken과 클라이언트가 보낸 토큰 비교 (RTR 정책 등 방어)
        if (!refreshToken.equals(user.getRefreshToken())) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        // [Flow 4] 검증 통과 시 두 토큰 모두 갱신 (보안 강화) 후 DB 업데이트
        String newAccessToken  = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId());
        user.setRefreshToken(newRefreshToken);

        return new TokenResponse(newAccessToken, newRefreshToken, user.getRole().name());
    }

    @Override
    public void logout(Long userId, String accessToken) {
        // [Flow 1] 로그아웃 시 DB의 RefreshToken을 비워서 자동 로그인(Refresh) 차단
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.setRefreshToken(null);

        // [Flow 2] 아직 만료되지 않은 AccessToken을 블랙리스트(Redis)에 등록해 더 이상 사용할 수 없도록 무효화
        if (accessToken != null && jwtUtil.isValid(accessToken)) {
            tokenBlacklistService.blacklist(accessToken, jwtUtil.getExpiration(accessToken));
        }
    }
}
