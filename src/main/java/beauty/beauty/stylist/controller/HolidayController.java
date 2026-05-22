package beauty.beauty.stylist.controller;

import beauty.beauty.global.annotation.LoginUserId;
import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.stylist.entity.StylistHoliday;
import beauty.beauty.stylist.entity.StylistProfile;
import beauty.beauty.stylist.repository.StylistHolidayRepository;
import beauty.beauty.stylist.repository.StylistProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stylist/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final StylistHolidayRepository holidayRepository;
    private final StylistProfileRepository stylistProfileRepository;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getHolidays(
            @LoginUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        StylistProfile profile = stylistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_PROFILE_NOT_FOUND));

        List<Map<String, Object>> result = holidayRepository
                .findByStylistProfileIdAndHolidayDateBetween(profile.getId(), from, to)
                .stream()
                .map(h -> Map.<String, Object>of(
                        "id", h.getId(),
                        "date", h.getHolidayDate().toString(),
                        "reason", h.getReason() != null ? h.getReason() : ""))
                .toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Void> addHoliday(
            @LoginUserId Long userId,
            @RequestBody Map<String, String> body) {

        StylistProfile profile = stylistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_PROFILE_NOT_FOUND));

        LocalDate date = LocalDate.parse(body.get("date"));
        String reason = body.get("reason");

        if (!holidayRepository.existsByStylistProfileIdAndHolidayDate(profile.getId(), date)) {
            holidayRepository.save(StylistHoliday.builder()
                    .stylistProfile(profile)
                    .holidayDate(date)
                    .reason(reason)
                    .build());
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    @Transactional
    public ResponseEntity<Void> removeHoliday(
            @LoginUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        StylistProfile profile = stylistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_PROFILE_NOT_FOUND));

        holidayRepository.deleteByStylistProfileIdAndHolidayDate(profile.getId(), date);
        return ResponseEntity.ok().build();
    }
}
