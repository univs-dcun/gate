package ai.univs.gate.facade.sdk.application.usecase;

import ai.univs.gate.facade.sdk.application.service.SdkQrCodeService;
import ai.univs.gate.shared.jwt.JwtTokenProvider;
import ai.univs.gate.modules.match.application.input.LivenessInput;
import ai.univs.gate.modules.match.application.result.LivenessResult;
import ai.univs.gate.modules.match.application.usecase.LivenessUseCase;
import ai.univs.gate.shared.web.enums.CallerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class SdkLivenessUseCase {

    private final SdkQrCodeService sdkQrCodeService;
    private final JwtTokenProvider jwtTokenProvider;
    private final LivenessUseCase livenessUseCase;

    public LivenessResult execute(String code,
                                  MultipartFile matchingFaceImage,
                                  String transactionUuid
    ) {
        String token = sdkQrCodeService.getToken(code);
        Long accountId = jwtTokenProvider.getAccountIdFromToken(token);
        String apiKey = jwtTokenProvider.getApiKeyFromToken(token);

        var input = new LivenessInput(
                CallerType.SDK,
                accountId,
                apiKey,
                matchingFaceImage,
                transactionUuid);
        var result = livenessUseCase.execute(input);

        // QR 사용 완료 처리
        if (result.success()) sdkQrCodeService.consumeCode(code);

        return result;
    }
}
