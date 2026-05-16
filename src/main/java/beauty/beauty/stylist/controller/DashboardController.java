package beauty.beauty.stylist.controller;

import beauty.beauty.global.annotation.LoginUserId;
import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.stylist.entity.StylistDailyStat;
import beauty.beauty.stylist.entity.StylistProfile;
import beauty.beauty.stylist.repository.StylistDailyStatRepository;
import beauty.beauty.stylist.repository.StylistProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stylist/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final StylistDailyStatRepository statRepository;
    private final StylistProfileRepository stylistProfileRepository;

    // 미용사 전용 대시보드 통계 조회 API (예: 이번 달 전체 통계)
    @GetMapping("/stats")
    public ResponseEntity<List<StylistDailyStat>> getStats(
            @LoginUserId Long userId,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        StylistProfile profile = stylistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_PROFILE_NOT_FOUND));

        // [Flow 3] 대시보드 조회
        // 클라이언트(앱)가 대시보드를 열었을 때, 무거운 예약 테이블을 JOIN/SUM 하지 않고
        // 미리 정산된 통계 테이블에서 O(1)에 가깝게 데이터를 바로 꺼내줍니다.
        List<StylistDailyStat> stats = statRepository.findByStylistProfileIdAndStatDateBetweenOrderByStatDateAsc(
                profile.getId(), LocalDate.parse(startDate), LocalDate.parse(endDate)
        );

        return ResponseEntity.ok(stats);
    }
}
