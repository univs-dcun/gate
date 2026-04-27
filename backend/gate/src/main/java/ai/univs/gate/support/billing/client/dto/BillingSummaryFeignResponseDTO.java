package ai.univs.gate.support.billing.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BillingSummaryFeignResponseDTO {
    private Long dbUsedCount;
    private Long dbStorageLimit;
    private Long verifyAllocated;
    private Long verifyLimit;
    private Long identifyAllocated;
    private Long identifyLimit;
    private Long livenessAllocated;
    private Long livenessLimit;
}
