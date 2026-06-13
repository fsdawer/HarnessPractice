package beauty.beauty.stylist.service;

import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.stylist.entity.StylistHoliday;
import beauty.beauty.stylist.entity.StylistProfile;
import beauty.beauty.stylist.repository.StylistHolidayRepository;
import beauty.beauty.stylist.repository.StylistProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HolidayService {

    private final StylistHolidayRepository holidayRepository;
    private final StylistProfileRepository stylistProfileRepository;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHolidays(Long userId, LocalDate from, LocalDate to) {
        StylistProfile profile = stylistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_PROFILE_NOT_FOUND));
        return holidayRepository
                .findByStylistProfileIdAndHolidayDateBetween(profile.getId(), from, to)
                .stream()
                .map(h -> Map.<String, Object>of(
                        "id",     h.getId(),
                        "date",   h.getHolidayDate().toString(),
                        "reason", h.getReason() != null ? h.getReason() : ""))
                .toList();
    }

    @Transactional
    public void addHoliday(Long userId, LocalDate date, String reason) {
        StylistProfile profile = stylistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_PROFILE_NOT_FOUND));
        if (!holidayRepository.existsByStylistProfileIdAndHolidayDate(profile.getId(), date)) {
            holidayRepository.save(StylistHoliday.builder()
                    .stylistProfile(profile)
                    .holidayDate(date)
                    .reason(reason)
                    .build());
        }
    }

    @Transactional
    public void removeHoliday(Long userId, LocalDate date) {
        StylistProfile profile = stylistProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.STYLIST_PROFILE_NOT_FOUND));
        holidayRepository.deleteByStylistProfileIdAndHolidayDate(profile.getId(), date);
    }
}
