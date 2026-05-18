package beauty.beauty.user.controller;

import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.global.kakao.KakaoGeocodingService;
import beauty.beauty.user.dto.ChangePasswordRequest;
import beauty.beauty.user.dto.UpdateUserRequest;
import beauty.beauty.user.dto.UserResponse;
import beauty.beauty.user.service.UserService;
import beauty.beauty.global.annotation.LoginUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final KakaoGeocodingService kakaoGeocodingService;

    // GET  /api/users/me               내 정보 조회
    @GetMapping("/me")
    public ResponseEntity<UserResponse> selectMyInformation(@LoginUserId Long userId) {
        UserResponse response = userService.selectMyInformation(userId);
        return ResponseEntity.ok(response);
    }

    // PUT  /api/users/me               내 정보 수정
    @PutMapping("/me")
    public ResponseEntity<UserResponse> changeMyInformation(@LoginUserId Long userId,
                                                            @RequestBody UpdateUserRequest updateUserRequest) {
        UserResponse response = userService.changeMyInformation(userId, updateUserRequest);
        return ResponseEntity.ok(response);
    }


    // PUT  /api/users/me/password      비밀번호 변경
    @PutMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(@LoginUserId Long userId,
                                                 @RequestBody ChangePasswordRequest changePasswordRequest) {
        userService.changeMyPassword(userId, changePasswordRequest);
        return ResponseEntity.ok().build();
    }



    // POST /api/users/me/upgrade      미용사로 전환
    @PostMapping("/me/upgrade")
    public ResponseEntity<UserResponse> upgradeToStylist(@LoginUserId Long userId,
                                                         @RequestBody beauty.beauty.user.dto.UpgradeToStylistRequest request) {
        UserResponse response = userService.upgradeToStylist(userId, request);
        return ResponseEntity.ok(response);
    }


    // DELETE /api/users/me            회원 탈퇴
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@LoginUserId Long userId) {
        userService.deleteMe(userId);
        return ResponseEntity.noContent().build();
    }

    // GET /api/users/location         좌표 → 구 이름 변환
    @GetMapping("/location")
    public ResponseEntity<Map<String, String>> getDistrictByCoords(
            @RequestParam double lat,
            @RequestParam double lng) {
        String district = kakaoGeocodingService.reverseGeocode(lat, lng);
        if (district == null || district.isBlank()) {
            throw new CustomException(ErrorCode.LOCATION_DETECTION_FAILED);
        }
        return ResponseEntity.ok(Map.of("district", district));
    }

    // PUT /api/users/me/district      위치(구) 인증
    @PutMapping("/me/district")
    public ResponseEntity<Void> verifyDistrict(@LoginUserId Long userId,
                                               @RequestBody Map<String, String> body) {
        userService.verifyDistrict(userId, body.get("district"));
        return ResponseEntity.ok().build();
    }
}
