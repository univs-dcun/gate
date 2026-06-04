package ai.univs.gate.facade.sdk.application.usecase;

import ai.univs.gate.facade.sdk.application.input.CreateUserByTokenInput;
import ai.univs.gate.facade.sdk.application.service.SdkQrCodeService;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.face_media.application.result.FaceMediaResult;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.jwt.JwtTokenProvider;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.face_media.CreateFaceMediaServiceResult;
import ai.univs.gate.support.face_media.FaceMediaService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateUserByTokenUseCase {

    private final SdkQrCodeService sdkQrCodeService;
    private final JwtTokenProvider jwtTokenProvider;
    private final FaceMediaService faceMediaService;
    private final FileService fileService;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional
    public FaceMediaResult execute(CreateUserByTokenInput input) {
        // 토큰으로 apiKey 조회
        String token = sdkQrCodeService.getToken(input.code());
        Long accountId = jwtTokenProvider.getAccountIdFromToken(token);
        String apiKey = jwtTokenProvider.getApiKeyFromToken(token);

        ApiKey findApiKey = apiKeyService.findByApiKey(apiKey);

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(findApiKey.getProject());

        // SDK 활성화 여부 체크
        projectSettingsService.validateSdkEnabled(findProjectSettings);

        // 얼굴 인증 매체 생성
        CreateFaceMediaServiceResult result = faceMediaService.createFaceMedia(
                CallerType.SDK,
                accountId,
                apiKey,
                input.faceImage(),
                input.userDescription(),
                input.username(),
                input.transactionUuid());
        return FaceMediaResult.from(result.faceMedia(), result.livenessChecked(), fileService.getFileServerPath(), findProjectSettings.getConsentEnabled());
    }
}
