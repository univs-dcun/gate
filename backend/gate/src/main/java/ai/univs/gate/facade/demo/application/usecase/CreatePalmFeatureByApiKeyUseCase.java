package ai.univs.gate.facade.demo.application.usecase;

import ai.univs.gate.facade.demo.application.input.CreatePalmFeatureByApiKeyInput;
import ai.univs.gate.modules.feature.application.result.palm.PalmFeatureResult;
import ai.univs.gate.support.feature.palm.CreatePalmFeatureServiceResult;
import ai.univs.gate.support.feature.palm.PalmFeatureService;
import ai.univs.gate.support.file.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ai.univs.gate.shared.web.enums.CallerType;

@Component
@RequiredArgsConstructor
public class CreatePalmFeatureByApiKeyUseCase {

    private final PalmFeatureService palmFeatureService;
    private final FileService fileService;

    public PalmFeatureResult execute(CreatePalmFeatureByApiKeyInput input) {
        // UG-336: 동의 값은 서비스가 등록 전에 읽어 돌려준다. 예전에는 여기서 API 키와 설정을 따로
        // 먼저 읽었는데, 서비스도 같은 조회를 맨 앞에서 다시 한다 — 조회가 두 번이고, 업로드 여부
        // (서비스가 읽은 값)와 응답의 이미지 노출(여기서 읽은 값)이 서로 다른 조회에서 나왔다.
        CreatePalmFeatureServiceResult result = palmFeatureService.createPalmFeature(
                CallerType.DEMO,
                input.accountId(),
                input.apiKey(),
                input.featureImage(),
                input.description(),
                input.transactionUuid(),
                // UG-333: 데모 등록은 고객사 식별자를 받지 않는다.
                null);

        return PalmFeatureResult.from(result.biometricFeature(), result.livenessChecked(),
                fileService.getFileServerPath(), result.consentEnabled());
    }
}
