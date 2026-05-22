package beauty.beauty.stylist.repository;

import beauty.beauty.stylist.entity.StylistHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface StylistHolidayRepository extends JpaRepository<StylistHoliday, Long> {
    boolean existsByStylistProfileIdAndHolidayDate(Long stylistProfileId, LocalDate holidayDate);
    List<StylistHoliday> findByStylistProfileIdAndHolidayDateBetween(Long stylistProfileId, LocalDate from, LocalDate to);
    void deleteByStylistProfileIdAndHolidayDate(Long stylistProfileId, LocalDate holidayDate);
}
