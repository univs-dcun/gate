package ai.univs.gate.facade.sdk.application.usecase;

import ai.univs.gate.facade.sdk.application.service.SdkQrCodeService;
import ai.univs.gate.shared.jwt.JwtTokenProvider;
import ai.univs.gate.modules.match.application.input.VerifyByFaceIdInput;
import ai.univs.gate.modules.match.application.result.VerifyByFaceIdResult;
import ai.univs.gate.modules.match.application.usecase.VerifyByFaceIdUseCase;
import ai.univs.gate.shared.web.enums.CallerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class SdkVerifyUseCase {

    private final SdkQrCodeService sdkQrCodeService;
    private final JwtTokenProvider jwtTokenProvider;
    private final VerifyByFaceIdUseCase verifyByFaceIdUseCase;

    public VerifyByFaceIdResult execute(String code,
                                        MultipartFile matchingFaceImage,
                                        String transactionUuid
    ) {
        String token = sdkQrCodeService.getToken(code);
        String accountId = jwtTokenProvider.getAccountIdFromToken(token).toString();
        String apiKey = jwtTokenProvider.getApiKeyFromToken(token);
        String faceId = jwtTokenProvider.getFaceIdFromToken(token);

        var input = new VerifyByFaceIdInput(
                CallerType.SDK,
                Long.parseLong(accountId),
                apiKey,
                faceId,
                matchingFaceImage,
                transactionUuid);
        var result = verifyByFaceIdUseCase.execute(input);

        // QR 사용 완료 처리
        if (result.success()) sdkQrCodeService.consumeCode(code);

        return result;
    }
}
