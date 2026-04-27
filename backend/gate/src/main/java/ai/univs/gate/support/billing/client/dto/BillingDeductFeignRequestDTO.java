package ai.univs.gate.support.billing.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BillingDeductFeignRequestDTO {
    private Long projectId;
    private Long accountId;
}
