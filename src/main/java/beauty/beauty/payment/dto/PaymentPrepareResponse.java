package beauty.beauty.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPrepareResponse {

    private Long paymentId;
    private String orderId;
    private int amount;         // 실제 결제 금액 (할인 후)
    private int originalAmount; // 원래 금액
    private int discountAmount; // 할인 금액
}
