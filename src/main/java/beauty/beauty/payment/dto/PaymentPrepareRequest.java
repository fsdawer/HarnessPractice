package beauty.beauty.payment.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PaymentPrepareRequest {

    private Long reservationId;
    private Long userCouponId;
}
