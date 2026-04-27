package ai.univs.gate.modules.project.application.usecase;

import ai.univs.gate.modules.project.application.input.ProjectQuery;
import ai.univs.gate.modules.project.application.result.ProjectSummaryResult;
import ai.univs.gate.modules.project.application.result.ProjectsResult;
import ai.univs.gate.modules.project.domain.repository.ProjectRepository;
import ai.univs.gate.shared.usecase.result.CustomPageResult;
import ai.univs.gate.support.billing.client.BillingClient;
import ai.univs.gate.support.billing.client.dto.ProjectBillingSummaryFeignResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GetProjectsUseCase {

    private final ProjectRepository projectRepository;
    private final BillingClient billingClient;

    @Transactional(readOnly = true)
    public ProjectsResult execute(ProjectQuery query) {
        long totalCount = projectRepository.countByAccountIdAndIsDeletedFalse(query.accountId());
        var pagedProjects = projectRepository.findByAccountIdAndIsDeletedFalse(query);

        List<ProjectSummaryResult> coreResults = pagedProjects.getContent();

        List<Long> projectIds = coreResults.stream()
                .map(ProjectSummaryResult::projectId)
                .toList();

        if (projectIds.isEmpty()) {
            return new ProjectsResult(
                    coreResults,
                    CustomPageResult.from(
                        pagedProjects.getPageable(),
                        pagedProjects.getTotalElements(),
                        pagedProjects.getTotalPages(),
                        totalCount)
            );
        }

        Map<Long, ProjectBillingSummaryFeignResponseDTO> billingMap =
                billingClient.getSubscriptionSummaryBatch(projectIds)
                        .stream()
                        .collect(Collectors.toMap(ProjectBillingSummaryFeignResponseDTO::getProjectId, b -> b));

        List<ProjectSummaryResult> results = coreResults.stream()
                .map(core -> {
                    ProjectBillingSummaryFeignResponseDTO b = billingMap.get(core.projectId());
                    if (b == null) return core;
                    return new ProjectSummaryResult(
                            core.projectId(),
                            core.projectName(),
                            core.projectDescription(),
                            core.status(),
                            core.projectType(),
                            core.projectModuleType(),
                            core.packageKey(),
                            b.getPlanType(),
                            b.getStartedAt(),
                            b.getNextBillingAt(),
                            b.getDbStorageLimit(),
                            b.getDbUsedCount(),
                            core.countUserRegistration(),
                            b.getVerifyAllocated() - b.getVerifyLimit(),
                            b.getVerifyAllocated(),
                            core.countVerify(),
                            b.getIdentifyAllocated() - b.getIdentifyLimit(),
                            b.getIdentifyAllocated(),
                            core.countIdentify(),
                            b.getLivenessAllocated() - b.getLivenessLimit(),
                            b.getLivenessAllocated(),
                            core.countLiveness(),
                            core.createdAt(),
                            core.updatedAt(),
                            core.apiKey()
                    );
                })
                .toList();

        CustomPageResult page = CustomPageResult.from(
                pagedProjects.getPageable(),
                pagedProjects.getTotalElements(),
                pagedProjects.getTotalPages(),
                totalCount);

        return new ProjectsResult(results, page);
    }
}
