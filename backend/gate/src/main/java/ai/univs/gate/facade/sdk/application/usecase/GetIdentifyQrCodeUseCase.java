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
public class GetIdentifyQrCodeUseCase {

    private final JwtTokenProvider jwtTokenProvider;
    private final QrCodeService qrCodeService;
    private final SdkQrCodeRepository sdkQrCodeRepository;
    private final ApiKeyService apiKeyService;
    private final ProjectService projectService;
    private final ProjectSettingsService projectSettingsService;

    @Value("${jwt.create-user-token-expiry}")
    private long qrCodeTokenExpiry;

    @Transactional
    public QrCodeResult execute(Long accountId, String apiKey) {
        ApiKey findApiKey = apiKeyService.findByApiKey(apiKey);

        // 프로젝트 모듈 타입 'FACE' 확인
        projectService.validateFaceModuleType(findApiKey.getProject());

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(findApiKey.getProject());

        // SDK 활성화 여부 체크
        projectSettingsService.validateSdkEnabled(findProjectSettings);

        // 1:N 매칭 QR 생성
        String token = jwtTokenProvider.createQrCodeTokenForIdentify(accountId.toString(), apiKey);
        String code = UUID.randomUUID().toString();
        sdkQrCodeRepository.save(SdkQrCode.builder()
                .code(code)
                .token(token)
                .type(SdkQrCodeType.IDENTIFY)
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(qrCodeTokenExpiry / 1000))
                .isUsed(false)
                .build());
        byte[] qr = qrCodeService.generateQrCode("match", code);

        // 1:N 매칭 링크 생성
        String link = qrCodeService.buildUrl("match", code);

        return new QrCodeResult(Base64.getEncoder().encodeToString(qr), link);
    }
}
