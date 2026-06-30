package ai.univs.gate.modules.feature.application.usecase.palm;

import ai.univs.gate.modules.feature.application.input.palm.CreatePalmFeatureInput;
import ai.univs.gate.modules.feature.application.result.palm.PalmFeatureResult;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.palm_feature.CreatePalmFeatureServiceResult;
import ai.univs.gate.support.palm_feature.PalmFeatureService;
import ai.univs.gate.support.project.ProjectSettingsService;
import ai.univs.gate.shared.web.enums.CallerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreatePalmFeatureUseCase {

    private final PalmFeatureService palmFeatureService;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    public PalmFeatureResult execute(CreatePalmFeatureInput input) {
        CreatePalmFeatureServiceResult result = palmFeatureService.createPalmFeature(
                input.accountId(),
                input.apiKey(),
                input.featureImage(),
                input.description(),
                input.transactionUuid());

        var apiKey = apiKeyService.findByApiKey(input.apiKey());
        ProjectSettings settings = projectSettingsService.findByProject(apiKey.getProject());

        return PalmFeatureResult.from(result.biometricFeature(), result.livenessChecked(),
                fileService.getFileServerPath(), settings.getConsentEnabled());
    }
}
