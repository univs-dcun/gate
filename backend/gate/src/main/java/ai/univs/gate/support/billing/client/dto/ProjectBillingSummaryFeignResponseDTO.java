package ai.univs.gate.support.billing.client.dto;

import ai.univs.gate.shared.billing.PlanType;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class ProjectBillingSummaryFeignResponseDTO {
    private Long projectId;
    private PlanType planType;
    private LocalDateTime startedAt;
    private LocalDateTime nextBillingAt;
    private Long dbStorageLimit;
    private Long dbUsedCount;
    private Long verifyAllocated;
    private Long verifyLimit;
    private Long identifyAllocated;
    private Long identifyLimit;
    private Long livenessAllocated;
    private Long livenessLimit;
}
