package beauty.beauty.user.service;

import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.user.dto.ChangePasswordRequest;
import beauty.beauty.user.dto.UpdateUserRequest;
import beauty.beauty.user.dto.UpgradeToStylistRequest;
import beauty.beauty.user.dto.UserResponse;
import beauty.beauty.user.entity.User;
import beauty.beauty.user.repository.UserRepository;
import beauty.beauty.stylist.entity.Salon;
import beauty.beauty.stylist.entity.StylistProfile;
import beauty.beauty.stylist.repository.SalonRepository;
import beauty.beauty.stylist.repository.StylistProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final StylistProfileRepository stylistProfileRepository;
    private final SalonRepository salonRepository;
    private final PasswordEncoder passwordEncoder;



    // GET  /api/users/me               내 정보 조회
    @Override
    @Transactional(readOnly = true)
    public UserResponse selectMyInformation(Long userId) {
        User user = findUserById(userId);
        return UserResponse.from(user);
    }



    // PUT  /api/users/me 내 정보 수정
    @Override
    @Transactional
    public UserResponse changeMyInformation(Long userId, UpdateUserRequest updateUserRequest) {
        User user = findUserById(userId);

        // null이 아닌 필드만 선택적으로 업데이트
        // 업데이트된 이름을 가져와서 user.setname한다
        if (updateUserRequest.getName() != null) {
            user.setName(updateUserRequest.getName());
        }
        if (updateUserRequest.getPhone() != null) {
            user.setPhone(updateUserRequest.getPhone());
        }
        if (updateUserRequest.getProfileImg() != null) {
            user.setProfileImg(updateUserRequest.getProfileImg());
        }

        return UserResponse.from(user);
    }


    // PUT  /api/users/me/password      비밀번호 변경
    @Override
    @Transactional
    public void changeMyPassword(Long userId, ChangePasswordRequest changePasswordRequest) {
        User user = findUserById(userId);

        // 1. 비밀변호 변경하기 위해 내 비밀번호를 입력한 데이터 = dto.getCurrentPassword
        // -> 입력한 비밀번호와 DB에 있는 비밀번호와 같은지 값 비교 = user.getPassword
        if (!passwordEncoder.matches(changePasswordRequest.getCurrentPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 2. 새 비밀번호와 새 비밀번호 확인이 일치하는지 확인
        if (!changePasswordRequest.getNewPassword().equals(changePasswordRequest.getNewPasswordConfirm())) {
            throw new CustomException(ErrorCode.NEW_PASSWORD_MISMATCH);
        }

        // 3. 새 비밀번호를 BCrypt로 해싱 후 저장
        user.setPassword(passwordEncoder.encode(changePasswordRequest.getNewPassword()));
    }


    @Override
    @Transactional
    public void deleteMe(Long userId) {
        User user = findUserById(userId);
        user.setDeletedAt(Instant.now());
    }


    @Override
    @Transactional
    public UserResponse upgradeToStylist(Long userId, UpgradeToStylistRequest request) {
        User user = findUserById(userId);

        if (user.getRole() == User.Role.STYLIST) {
            throw new CustomException(ErrorCode.ALREADY_STYLIST);
        }

        // 1. 권한 변경
        user.setRole(User.Role.STYLIST);

        // 2. 미용실 생성 후 미용사 프로필 생성
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

        return UserResponse.from(user);
    }




    // PUT /api/users/me/district      위치(구) 인증
    @Override
    @Transactional
    public void verifyDistrict(Long userId, String district) {
        User user = findUserById(userId);
        user.setVerifiedDistrict(district);
    }

    // ID 조회 공통 메서드
    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
