package ai.univs.gate.modules.palm_feature.application.usecase;

import ai.univs.gate.modules.face_feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.palm_feature.application.input.UpdatePalmFeatureInput;
import ai.univs.gate.modules.palm_feature.application.result.PalmFeatureResult;
import ai.univs.gate.modules.palm_feature.domain.entity.PalmFeature;
import ai.univs.gate.modules.palm_feature.domain.repository.PalmFeatureRepository;
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

    private final PalmFeatureRepository palmFeatureRepository;
    private final FileService fileService;
    private final PalmService palmService;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsRepository projectSettingsRepository;
    private final ProjectSettingsService projectSettingsService;

    @Transactional
    public PalmFeatureResult execute(UpdatePalmFeatureInput input) {
        PalmFeature palmFeature = palmFeatureRepository.findByIdAndIsDeletedFalse(input.palmFeatureId())
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();
        if (!palmFeature.getProject().equals(project)) {
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
                    palmFeature.getFeatureId(),
                    input.transactionUuid(),
                    String.valueOf(input.accountId())));

            String featureId = palmService.registerPalm(new RegisterPalmFeignRequestDTO(
                    project.getBranchName(),
                    input.featureImage(),
                    input.transactionUuid(),
                    String.valueOf(input.accountId()),
                    projectSettingsService.isLivenessEnabled(projectSettings, FeatureType.PALM, LivenessOperation.REGISTER)));

            String featureImagePath = fileService.uploadIfConsent(input.featureImage(), projectSettings.getConsentEnabled());
            palmFeature.updateFeatureImagePath(featureImagePath);
            palmFeature.updateFeatureId(featureId);
        }

        palmFeature.updateInfo(input.description());

        return PalmFeatureResult.from(palmFeature, fileService.getFileServerPath(), projectSettings.getConsentEnabled());
    }
}
