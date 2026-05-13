package beauty.beauty.stylist.dto;

import beauty.beauty.reservation.entity.Reservation;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RecentReservationDto {

    private Long id;
    private String customerName;
    private String serviceName;
    private LocalDateTime reservedAt;
    private Reservation.Status status;
    private int totalPrice;

    public static RecentReservationDto from(Reservation r) {
        return RecentReservationDto.builder()
                .id(r.getId())
                .customerName(r.getUser().getName())
                .serviceName(r.getService().getName())
                .reservedAt(r.getReservedAt())
                .status(r.getStatus())
                .totalPrice(r.getTotalPrice())
                .build();
    }
}
