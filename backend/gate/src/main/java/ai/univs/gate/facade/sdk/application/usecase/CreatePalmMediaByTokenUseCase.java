package ai.univs.gate.facade.sdk.application.usecase;

import ai.univs.gate.facade.sdk.application.input.CreatePalmMediaByTokenInput;
import ai.univs.gate.facade.sdk.application.service.SdkQrCodeService;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.palm_media.application.result.PalmMediaResult;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.jwt.JwtTokenProvider;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.palm_media.CreatePalmMediaServiceResult;
import ai.univs.gate.support.palm_media.PalmMediaService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreatePalmMediaByTokenUseCase {

    private final SdkQrCodeService sdkQrCodeService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PalmMediaService palmMediaService;
    private final FileService fileService;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional
    public PalmMediaResult execute(CreatePalmMediaByTokenInput input) {
        String token = sdkQrCodeService.getToken(input.code());
        Long accountId = jwtTokenProvider.getAccountIdFromToken(token);
        String apiKey = jwtTokenProvider.getApiKeyFromToken(token);

        ApiKey findApiKey = apiKeyService.findByApiKey(apiKey);

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(findApiKey.getProject());

        projectSettingsService.validateSdkEnabled(findProjectSettings);

        CreatePalmMediaServiceResult result = palmMediaService.createPalmMedia(
                CallerType.SDK,
                accountId,
                apiKey,
                input.palmImage(),
                input.description(),
                input.username(),
                input.transactionUuid());
        return PalmMediaResult.from(
                result.palmMedia(),
                result.livenessChecked(),
                fileService.getFileServerPath(),
                findProjectSettings.getConsentEnabled());
    }
}
