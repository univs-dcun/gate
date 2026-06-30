package ai.univs.gate.modules.feature.application.usecase.face;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.face.UpdateFaceFeatureInput;
import ai.univs.gate.modules.feature.application.result.face.FaceFeatureResult;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.UpdateFaceFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.repository.ProjectSettingsRepository;
import ai.univs.gate.support.project.ProjectSettingsService;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.face.FaceService;
import ai.univs.gate.support.file.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateFaceFeatureUseCase {

    private final BiometricFeatureRepository biometricFeatureRepository;
    private final FileService fileService;
    private final FaceService faceService;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsRepository projectSettingsRepository;
    private final ProjectSettingsService projectSettingsService;

    @Transactional
    public FaceFeatureResult execute(UpdateFaceFeatureInput input) {
        BiometricFeature biometricFeature = biometricFeatureRepository.findByIdAndTypeAndIsDeletedFalse(input.faceFeatureId(), FeatureType.FACE)
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();
        if (!biometricFeature.getProject().equals(project)) {
            log.error("Not faceFeature who created based on this apikey. accountId: {}, apiKey: {}, faceFeatureId: {}",
                    input.accountId(), input.apiKey(), input.faceFeatureId());
            throw new CustomGateException(ErrorType.INVALID_USER);
        }

        ProjectSettings projectSettings = projectSettingsRepository.findByProject(project)
                .orElseThrow(() -> new CustomGateException(ErrorType.SETTINGS_NOT_FOUND));

        if (input.hasImage()) {
            input.validationFileExtension();

            String featureImagePath = fileService.uploadIfConsent(input.featureImage(), projectSettings.getConsentEnabled());
            biometricFeature.updateFeatureImagePath(featureImagePath);

            var updateRequest = new UpdateFaceFeignRequestDTO(
                    project.getBranchName(),
                    biometricFeature.getFeatureId(),
                    input.featureImage(),
                    input.transactionUuid(),
                    String.valueOf(input.accountId()),
                    projectSettingsService.isLivenessEnabled(projectSettings, FeatureType.FACE, LivenessOperation.REGISTER),
                    projectSettingsService.isLivenessEnabled(projectSettings, FeatureType.FACE, LivenessOperation.REGISTER));
            faceService.updateFace(updateRequest);
        }

        biometricFeature.updateInfo(input.description());

        return FaceFeatureResult.from(biometricFeature, fileService.getFileServerPath(), projectSettings.getConsentEnabled());
    }
}
