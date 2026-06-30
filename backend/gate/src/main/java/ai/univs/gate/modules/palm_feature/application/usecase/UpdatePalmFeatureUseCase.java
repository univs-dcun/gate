package ai.univs.gate.modules.palm_feature.application.usecase;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.palm_feature.application.input.UpdatePalmFeatureInput;
import ai.univs.gate.modules.palm_feature.application.result.PalmFeatureResult;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.DeletePalmFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.RegisterPalmFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.repository.ProjectSettingsRepository;
import ai.univs.gate.support.project.ProjectSettingsService;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.palm.PalmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdatePalmFeatureUseCase {

    private final BiometricFeatureRepository biometricFeatureRepository;
    private final FileService fileService;
    private final PalmService palmService;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsRepository projectSettingsRepository;
    private final ProjectSettingsService projectSettingsService;

    @Transactional
    public PalmFeatureResult execute(UpdatePalmFeatureInput input) {
        BiometricFeature biometricFeature = biometricFeatureRepository.findByIdAndTypeAndIsDeletedFalse(input.palmFeatureId(), FeatureType.PALM)
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();
        if (!biometricFeature.getProject().equals(project)) {
            log.error("Not palmFeature who created based on this apikey. accountId: {}, apiKey: {}, palmFeatureId: {}",
                    input.accountId(), input.apiKey(), input.palmFeatureId());
            throw new CustomGateException(ErrorType.INVALID_USER);
        }

        ProjectSettings projectSettings = projectSettingsRepository.findByProject(project)
                .orElseThrow(() -> new CustomGateException(ErrorType.SETTINGS_NOT_FOUND));

        if (input.featureImage() != null && !input.featureImage().isEmpty()) {
            // palm-service는 update 미지원: 기존 팜 삭제 후 재등록
            palmService.deletePalm(new DeletePalmFeignRequestDTO(
                    project.getBranchName(),
                    biometricFeature.getFeatureId(),
                    input.transactionUuid(),
                    String.valueOf(input.accountId())));

            String featureId = palmService.registerPalm(new RegisterPalmFeignRequestDTO(
                    project.getBranchName(),
                    input.featureImage(),
                    input.transactionUuid(),
                    String.valueOf(input.accountId()),
                    projectSettingsService.isLivenessEnabled(projectSettings, FeatureType.PALM, LivenessOperation.REGISTER)));

            String featureImagePath = fileService.uploadIfConsent(input.featureImage(), projectSettings.getConsentEnabled());
            biometricFeature.updateFeatureImagePath(featureImagePath);
            biometricFeature.updateFeatureId(featureId);
        }

        biometricFeature.updateInfo(input.description());

        return PalmFeatureResult.from(biometricFeature, fileService.getFileServerPath(), projectSettings.getConsentEnabled());
    }
}
