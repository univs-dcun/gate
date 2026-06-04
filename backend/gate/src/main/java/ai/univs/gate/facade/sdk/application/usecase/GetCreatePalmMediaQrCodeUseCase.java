package ai.univs.gate.facade.sdk.application.usecase;

import ai.univs.gate.facade.sdk.application.result.QrCodeResult;
import ai.univs.gate.facade.sdk.domain.entity.SdkQrCode;
import ai.univs.gate.facade.sdk.domain.enums.SdkQrCodeType;
import ai.univs.gate.facade.sdk.domain.repository.SdkQrCodeRepository;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.jwt.JwtTokenProvider;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.project.ProjectService;
import ai.univs.gate.support.project.ProjectSettingsService;
import ai.univs.gate.support.qr.QrCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GetCreatePalmMediaQrCodeUseCase {

    private final QrCodeService qrCodeService;
    private final JwtTokenProvider jwtTokenProvider;
    private final SdkQrCodeRepository sdkQrCodeRepository;
    private final ApiKeyService apiKeyService;
    private final ProjectService projectService;
    private final ProjectSettingsService projectSettingsService;

    @Value("${jwt.create-user-token-expiry}")
    private long qrCodeTokenExpiry;

    @Transactional
    public QrCodeResult execute(Long accountId, String apiKey) {
        ApiKey findApiKey = apiKeyService.findByApiKey(apiKey);

        projectService.validatePalmModuleType(findApiKey.getProject());

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(findApiKey.getProject());

        projectSettingsService.validateSdkEnabled(findProjectSettings);

        String token = jwtTokenProvider.createQrCodeTokenForCreatingPalmMedia(accountId.toString(), apiKey);
        String code = UUID.randomUUID().toString();
        sdkQrCodeRepository.save(SdkQrCode.builder()
                .code(code)
                .token(token)
                .type(SdkQrCodeType.CREATE_PALM_MEDIA)
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(qrCodeTokenExpiry / 1000))
                .isUsed(false)
                .build());
        byte[] qrCode = qrCodeService.generateQrCode("register-palm", code);
        String link = qrCodeService.buildUrl("register-palm", code);

        return new QrCodeResult(Base64.getEncoder().encodeToString(qrCode), link);
    }
}
