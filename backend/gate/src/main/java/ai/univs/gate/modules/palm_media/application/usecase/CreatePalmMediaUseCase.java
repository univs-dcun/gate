package ai.univs.gate.modules.palm_media.application.usecase;

import ai.univs.gate.modules.palm_media.application.input.CreatePalmMediaInput;
import ai.univs.gate.modules.palm_media.application.result.PalmMediaResult;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.palm_media.CreatePalmMediaServiceResult;
import ai.univs.gate.support.palm_media.PalmMediaService;
import ai.univs.gate.support.project.ProjectSettingsService;
import ai.univs.gate.shared.web.enums.CallerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreatePalmMediaUseCase {

    private final PalmMediaService palmMediaService;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    public PalmMediaResult execute(CreatePalmMediaInput input) {
        CreatePalmMediaServiceResult result = palmMediaService.createPalmMedia(
                CallerType.API,
                input.accountId(),
                input.apiKey(),
                input.palmImage(),
                input.description(),
                input.username(),
                input.transactionUuid());

        var apiKey = apiKeyService.findByApiKey(input.apiKey());
        ProjectSettings settings = projectSettingsService.findByProject(apiKey.getProject());

        return PalmMediaResult.from(result.palmMedia(), result.livenessChecked(),
                fileService.getFileServerPath(), settings.getConsentEnabled());
    }
}
