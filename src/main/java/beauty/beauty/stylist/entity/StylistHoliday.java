package beauty.beauty.stylist.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "stylist_holidays",
       uniqueConstraints = @UniqueConstraint(columnNames = {"stylist_profile_id", "holiday_date"}))
@Getter
@NoArgsConstructor @AllArgsConstructor @Builder
public class StylistHoliday {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stylist_profile_id", nullable = false)
    private StylistProfile stylistProfile;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(length = 200)
    private String reason;
}
