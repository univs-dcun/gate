package ai.univs.gate.modules.project.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.domain.repository.ApiKeyRepository;
import ai.univs.gate.modules.face_feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.application.input.CreateProjectInput;
import ai.univs.gate.modules.project.application.result.ProjectResult;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectLivenessSetting;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.modules.project.domain.repository.ProjectLivenessSettingRepository;
import ai.univs.gate.modules.project.domain.repository.ProjectRepository;
import ai.univs.gate.modules.project.domain.repository.ProjectSettingsRepository;
import ai.univs.gate.support.api_key.ApiKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static ai.univs.gate.shared.utils.DateTimeUtil.nowUtc;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreateProjectUseCase {

    private final ProjectRepository projectRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ProjectSettingsRepository projectSettingsRepository;
    private final ProjectLivenessSettingRepository livenessSettingRepository;
    private final ApiKeyGenerator apiKeyGenerator;

    @Value("${api-key.expiry-days}")
    private int apiKeyExpiryDays;

    @Transactional
    public ProjectResult execute(CreateProjectInput input) {
        log.info("Creating project for userId: {}", input.accountId());

        Project project = Project.builder()
                .accountId(input.accountId())
                .projectName(input.projectName())
                .projectDescription(input.projectDescription())
                .colorTag(input.colorTag())
                .branchName(UUID.randomUUID().toString())
                .status(ProjectStatus.ACTIVE)
                .isDeleted(false)
                .build();
        Project savedProject = projectRepository.save(project);
        log.info("Project created: projectId={}", savedProject.getId());

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

        ProjectSettings projectSettings = ProjectSettings.builder()
                .project(savedProject)
                .consentEnabled(false)
                .build();
        projectSettingsRepository.save(projectSettings);

        // FACE, PALM 모두 liveness 기본값 저장
        livenessSettingRepository.saveAll(defaultLivenessSettings(projectSettings, FeatureType.FACE));
        livenessSettingRepository.saveAll(defaultLivenessSettings(projectSettings, FeatureType.PALM));
        log.info("Project settings initialized: projectId={}", projectSettings.getId());

        return ProjectResult.from(savedProject, savedApiKey.getApiKey());
    }

    private List<ProjectLivenessSetting> defaultLivenessSettings(ProjectSettings settings, FeatureType moduleType) {
        return List.of(
                buildSetting(settings, moduleType, LivenessOperation.REGISTER, false),
                buildSetting(settings, moduleType, LivenessOperation.IDENTIFY, true),
                buildSetting(settings, moduleType, LivenessOperation.VERIFY_ID, true),
                buildSetting(settings, moduleType, LivenessOperation.VERIFY_IMAGE, true)
        );
    }

    private ProjectLivenessSetting buildSetting(ProjectSettings settings, FeatureType moduleType,
                                                 LivenessOperation operation, boolean enabled) {
        return ProjectLivenessSetting.builder()
                .projectSettings(settings)
                .moduleType(moduleType)
                .operation(operation)
                .enabled(enabled)
                .build();
    }
}
