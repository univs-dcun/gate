package ai.univs.gate.facade.demo.application.usecase;

import ai.univs.gate.facade.demo.application.input.CreateFaceFeatureByApiKeyInput;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
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
public class CreateFaceFeatureByApiKeyUseCase {

    private final FaceFeatureService faceFeatureService;
    private final FileService fileService;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional
    public FaceFeatureResult execute(CreateFaceFeatureByApiKeyInput input) {
        ApiKey findApiKey = apiKeyService.findByApiKey(input.apiKey());

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(findApiKey.getProject());

        // 데모 활성화 여부 체크

        CreateFaceFeatureServiceResult result = faceFeatureService.createFaceFeature(
                CallerType.DEMO,
                input.accountId(),
                input.apiKey(),
                input.featureImage(),
                input.description(),
                input.username(),
                input.transactionUuid());
        return FaceFeatureResult.from(result.faceFeature(), result.livenessChecked(), fileService.getFileServerPath(), findProjectSettings.getConsentEnabled());
    }
}
