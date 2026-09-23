package ai.univs.gate.facade.demo.application.usecase;

import ai.univs.gate.facade.demo.application.input.CreateFaceFeatureByApiKeyInput;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.result.face.FaceFeatureResult;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.face.CreateFaceFeatureServiceResult;
import ai.univs.gate.support.feature.face.FaceFeatureService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ai.univs.gate.shared.web.enums.CallerType;

@Component
@RequiredArgsConstructor
public class CreateFaceFeatureByApiKeyUseCase {

    private final FaceFeatureService faceFeatureService;
    private final FileService fileService;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsService projectSettingsService;

    public FaceFeatureResult execute(CreateFaceFeatureByApiKeyInput input) {
        ApiKey findApiKey = apiKeyService.findByApiKeyUnverified(input.apiKey());

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(findApiKey.getProject());

        CreateFaceFeatureServiceResult result = faceFeatureService.createFaceFeature(
                CallerType.DEMO,
                input.accountId(),
                input.apiKey(),
                input.featureImage(),
                input.description(),
                input.transactionUuid(),
                // UG-333: 데모 등록은 고객사 식별자를 받지 않는다.
                null);
        return FaceFeatureResult.from(result.biometricFeature(), result.livenessChecked(), fileService.getFileServerPath(), findProjectSettings.getConsentEnabled());
    }
}
