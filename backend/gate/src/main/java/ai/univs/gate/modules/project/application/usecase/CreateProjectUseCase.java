package ai.univs.gate.modules.project.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.domain.repository.ApiKeyRepository;
import ai.univs.gate.modules.project.application.input.CreateProjectInput;
import ai.univs.gate.modules.project.application.result.ProjectResult;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.modules.project.domain.repository.ProjectRepository;
import ai.univs.gate.modules.project.domain.repository.ProjectSettingsRepository;
import ai.univs.gate.support.api_key.ApiKeyGenerator;
import ai.univs.gate.support.billing.client.BillingClient;
import ai.univs.gate.support.billing.client.dto.ProjectInitFeignRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static ai.univs.gate.shared.utils.DateTimeUtil.nowUtc;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreateProjectUseCase {

    private final ProjectRepository projectRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ProjectSettingsRepository projectSettingsRepository;
    private final ApiKeyGenerator apiKeyGenerator;
    private final BillingClient billingClient;

    @Value("${api-key.expiry-days}")
    private int apiKeyExpiryDays;

    @Transactional
    public ProjectResult execute(CreateProjectInput input) {
        log.info("Creating project for userId: {}", input.accountId());

        // FREE 플랜 프로젝트 최대 10개 제한 (billing 정책 검증)
        billingClient.validateFreePlanLimit(input.accountId());

        // 프로젝트 생성
        Project project = Project.builder()
                .accountId(input.accountId())
                .projectName(input.projectName())
                .projectDescription(input.projectDescription())
                .branchName(UUID.randomUUID().toString())
                .status(ProjectStatus.ACTIVE)
                .projectType(input.projectType())
                .projectModuleType(input.projectModuleType())
                .isDeleted(false)
                .build();
        Project savedProject = projectRepository.save(project);
        log.info("Project created: projectId={}", savedProject.getId());

        // API Key 자동 발급
        String apiKey = apiKeyGenerator.generateApiKey();
        String secretKey = apiKeyGenerator.generateSecretKey();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(apiKeyExpiryDays);
        ApiKey newApiKey = ApiKey.builder()
                .project(project)
                .apiKey(apiKey)
                .secretKey(secretKey)
                .issuedAt(nowUtc())
                .expiresAt(expiresAt)
                .isActive(true)
                .build();
        ApiKey savedApiKey = apiKeyRepository.save(newApiKey);
        log.info("API Key issued: apiKeyId={}", savedApiKey.getId());

        // 프로젝트 설정 초기화
        ProjectSettings projectSettings = ProjectSettings.builder()
                .project(savedProject)
                .demoEnabled(true)
                .sdkEnabled(true)
                .consentEnabled(false)
                .livenessRecordingEnabled(false)
                .livenessIdentifyingEnabled(true)
                .livenessVerifyingEnabled(true)
                .build();
        projectSettingsRepository.save(projectSettings);
        log.info("Project settings initialized: projectId={}", projectSettings.getId());

        // 빌링 초기화 (Subscription, CreditBalance, FeatureLimit)
        billingClient.initializeProjectBilling(
                savedProject.getId(),
                new ProjectInitFeignRequestDTO(input.accountId()));

        return ProjectResult.from(savedProject, savedApiKey.getApiKey());
    }
}
