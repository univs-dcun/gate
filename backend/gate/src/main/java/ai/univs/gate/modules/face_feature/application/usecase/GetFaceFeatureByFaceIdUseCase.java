package ai.univs.gate.modules.face_feature.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.face_feature.application.input.GetFaceFeatureByFeatureIdInput;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetFaceFeatureByFaceIdUseCase {

    private final FaceFeatureRepository faceFeatureRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(readOnly = true)
    public FaceFeatureResult execute(GetFaceFeatureByFeatureIdInput input) {
        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();
        FaceFeature faceFeature = faceFeatureRepository.findByFaceIdAndProjectIdAndIsDeletedFalse(input.featureId(), project.getId())
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        ProjectSettings projectSettings = projectSettingsService.findByProject(project);
        return FaceFeatureResult.from(faceFeature, fileService.getFileServerPath(), projectSettings.getConsentEnabled());
    }
}
