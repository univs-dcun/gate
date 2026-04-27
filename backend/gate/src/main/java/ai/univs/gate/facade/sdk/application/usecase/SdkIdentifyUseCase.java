package ai.univs.gate.facade.sdk.application.usecase;

import ai.univs.gate.facade.sdk.application.service.SdkQrCodeService;
import ai.univs.gate.shared.jwt.JwtTokenProvider;
import ai.univs.gate.modules.match.application.input.IdentifyInput;
import ai.univs.gate.modules.match.application.result.IdentifyResult;
import ai.univs.gate.modules.match.application.usecase.IdentifyUseCase;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.web.enums.CallerType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class SdkIdentifyUseCase {

    private final SdkQrCodeService sdkQrCodeService;
    private final JwtTokenProvider jwtTokenProvider;
    private final IdentifyUseCase identifyUseCase;

    public IdentifyResult execute(String code,
                                  MultipartFile matchingFaceImage,
                                  String transactionUuid
    ) {
        String token = sdkQrCodeService.getToken(code);
        Long accountId = jwtTokenProvider.getAccountIdFromToken(token);
        String apiKey = jwtTokenProvider.getApiKeyFromToken(token);


        var input = new IdentifyInput(
                CallerType.SDK,
                accountId,
                apiKey,
                matchingFaceImage,
                TransactionUtil.useOrCreate(transactionUuid));
        var result = identifyUseCase.execute(input);

        // 1:1 매칭 성공시 QR 사용 완료 처리
        if (result.success()) sdkQrCodeService.consumeCode(code);

        return result;
    }
}
