package ai.univs.gate.support.billing.client;

import ai.univs.gate.support.billing.client.dto.*;
import ai.univs.gate.support.feign.CommonFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "billing-service", configuration = CommonFeignConfig.class)
public interface BillingClient {

    @PostMapping("/internal/billing/validate/{feature}")
    void validate(@PathVariable String feature, @RequestBody BillingOperationFeignRequestDTO request);

    @PostMapping("/internal/billing/deduct/{feature}")
    void deduct(@PathVariable String feature, @RequestBody BillingDeductFeignRequestDTO request);

    @PostMapping("/internal/billing/db-storage/validate")
    void validateDbStorage(@RequestBody BillingOperationFeignRequestDTO request);

    @PostMapping("/internal/billing/db-storage/increment")
    void incrementDbUsed(@RequestBody BillingOperationFeignRequestDTO request);

    @PostMapping("/internal/billing/db-storage/decrement")
    void decrementDbUsed(@RequestBody BillingOperationFeignRequestDTO request);

    @PostMapping("/internal/billing/projects/{projectId}/initialize")
    void initializeProjectBilling(@PathVariable Long projectId, @RequestBody ProjectInitFeignRequestDTO request);

    @DeleteMapping("/internal/billing/projects/{projectId}")
    void handleProjectDeletion(@PathVariable Long projectId);

    @GetMapping("/internal/billing/projects/free-plan-limit/validate")
    void validateFreePlanLimit(@RequestParam Long accountId);

    @GetMapping("/internal/billing/subscriptions/{projectId}/summary")
    BillingSummaryFeignResponseDTO getSubscriptionSummary(@PathVariable Long projectId);

    @PostMapping("/internal/billing/subscriptions/summary/batch")
    List<ProjectBillingSummaryFeignResponseDTO> getSubscriptionSummaryBatch(@RequestBody List<Long> projectIds);
}
