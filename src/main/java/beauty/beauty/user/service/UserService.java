package beauty.beauty.user.service;

import beauty.beauty.user.dto.ChangePasswordRequest;
import beauty.beauty.user.dto.UpdateUserRequest;
import beauty.beauty.user.dto.UserResponse;

public interface UserService {

    // GET  /api/users/me               내 정보 조회
    UserResponse selectMyInformation(Long userId);

    // PUT  /api/users/me               내 정보 수정
    UserResponse changeMyInformation(Long userId, UpdateUserRequest updateUserRequest);

    // PUT  /api/users/me/password      비밀번호 변경
    void changeMyPassword(Long userId, ChangePasswordRequest changePasswordRequest);

    // DELETE /api/users/me            회원 탈퇴
    void deleteMe(Long userId);

    // POST /api/users/me/upgrade      미용사로 전환 (카카오 등 일반 유저용)
    UserResponse upgradeToStylist(Long userId, beauty.beauty.user.dto.UpgradeToStylistRequest request);

    // PUT /api/users/me/district      위치(구) 인증
    void verifyDistrict(Long userId, String district);


}
