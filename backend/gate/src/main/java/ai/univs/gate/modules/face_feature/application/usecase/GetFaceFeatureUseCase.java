package ai.univs.gate.modules.face_feature.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.face_feature.application.input.GetFaceFeatureInput;
import ai.univs.gate.modules.face_feature.application.result.FaceFeatureResult;
import ai.univs.gate.modules.face_feature.domain.entity.FaceFeature;
import ai.univs.gate.modules.face_feature.domain.repository.FaceFeatureRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetFaceFeatureUseCase {

    private final FaceFeatureRepository faceFeatureRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(readOnly = true)
    public FaceFeatureResult execute(GetFaceFeatureInput input) {
        FaceFeature faceFeature = faceFeatureRepository.findByIdAndIsDeletedFalse(input.faceFeatureId())
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();
        if (!faceFeature.getProject().equals(project)) {
            log.error("Not faceFeature who created based on this apikey. accountId: {}, apiKey: {}, faceFeatureId: {}",
                    input.accountId(), input.apiKey(), input.faceFeatureId());
            throw new CustomGateException(ErrorType.INVALID_USER);
        }

        ProjectSettings projectSettings = projectSettingsService.findByProject(project);
        return FaceFeatureResult.from(faceFeature, fileService.getFileServerPath(), projectSettings.getConsentEnabled());
    }
}
