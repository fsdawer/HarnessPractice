package beauty.beauty.stylist.controller;

import beauty.beauty.global.annotation.LoginUserId;
import beauty.beauty.stylist.service.HolidayService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stylist/holidays")
@RequiredArgsConstructor
public class HolidayController {

    private final HolidayService holidayService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getHolidays(
            @LoginUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(holidayService.getHolidays(userId, from, to));
    }

    @PostMapping
    public ResponseEntity<Void> addHoliday(
            @LoginUserId Long userId,
            @RequestBody Map<String, String> body) {
        holidayService.addHoliday(userId, LocalDate.parse(body.get("date")), body.get("reason"));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> removeHoliday(
            @LoginUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        holidayService.removeHoliday(userId, date);
        return ResponseEntity.ok().build();
    }
}
