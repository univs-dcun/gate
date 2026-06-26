package ai.univs.gate.facade.demo.application.usecase;

import ai.univs.gate.facade.demo.application.input.CreatePalmFeatureByApiKeyInput;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.palm_feature.application.result.PalmFeatureResult;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.palm_feature.CreatePalmFeatureServiceResult;
import ai.univs.gate.support.palm_feature.PalmFeatureService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreatePalmFeatureByApiKeyUseCase {

    private final PalmFeatureService palmFeatureService;
    private final FileService fileService;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional
    public PalmFeatureResult execute(CreatePalmFeatureByApiKeyInput input) {
        ApiKey findApiKey = apiKeyService.findByApiKey(input.apiKey());

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(findApiKey.getProject());

        CreatePalmFeatureServiceResult result = palmFeatureService.createPalmFeature(
                input.accountId(),
                input.apiKey(),
                input.featureImage(),
                input.description(),
                input.transactionUuid());

        return PalmFeatureResult.from(result.palmFeature(), result.livenessChecked(),
                fileService.getFileServerPath(), findProjectSettings.getConsentEnabled());
    }
}
