package ai.univs.gate.modules.face_feature.application.usecase;

import ai.univs.gate.modules.face_feature.application.input.CreateFaceFeatureInput;
import ai.univs.gate.modules.face_feature.application.result.FaceFeatureResult;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.face_feature.CreateFaceFeatureServiceResult;
import ai.univs.gate.support.face_feature.FaceFeatureService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateFaceFeatureUseCase {

    private final FaceFeatureService faceFeatureService;
    private final FileService fileService;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional
    public FaceFeatureResult execute(CreateFaceFeatureInput input) {
        CreateFaceFeatureServiceResult result = faceFeatureService.createFaceFeature(
                input.accountId(),
                input.apiKey(),
                input.featureImage(),
                input.description(),
                input.transactionUuid());

        ProjectSettings projectSettings = projectSettingsService.findByProject(
                apiKeyService.findByApiKey(input.apiKey()).getProject());
        return FaceFeatureResult.from(result.faceFeature(), result.livenessChecked(), fileService.getFileServerPath(), projectSettings.getConsentEnabled());
    }
}
