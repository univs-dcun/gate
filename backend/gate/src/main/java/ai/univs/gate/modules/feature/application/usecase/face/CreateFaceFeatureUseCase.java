package ai.univs.gate.modules.feature.application.usecase.face;

import ai.univs.gate.modules.feature.application.result.face.FaceFeatureResult;
import ai.univs.gate.modules.feature.application.input.CreateFeatureInput;
import ai.univs.gate.support.feature.face.CreateFaceFeatureServiceResult;
import ai.univs.gate.support.feature.face.FaceFeatureService;
import ai.univs.gate.support.file.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ai.univs.gate.shared.web.enums.CallerType;

@Component
@RequiredArgsConstructor
public class CreateFaceFeatureUseCase {

    private final FaceFeatureService faceFeatureService;
    private final FileService fileService;

    /**
     * 트랜잭션을 열지 않는다 (UG-336) — 이유와 지연 연관이 안전한 근거는 쌍둥이인
     * {@code CreatePalmFeatureUseCase} 참고.
     */
    public FaceFeatureResult execute(CreateFeatureInput input) {
        CreateFaceFeatureServiceResult result = faceFeatureService.createFaceFeature(
                CallerType.API,
                input.accountId(),
                input.apiKey(),
                input.featureImage(),
                input.description(),
                input.transactionUuid(),
                input.externalKey());

        // 커밋 뒤에는 DB 를 다시 읽지 않는다. UG-281 반박 리뷰는 API 키 재조회를 같은 이유로
        // 뺐고(커밋 뒤 조회가 실패하면 특징점은 남은 채 오류가 나간다), UG-336 은 설정 재조회도
        // 뺐다 — 이 메서드에 트랜잭션이 없으므로 그 조회는 새 커넥션을 요구하고, 거기서 실패하면
        // 클라이언트의 재시도가 이중 등록이 된다. 동의 값은 서비스가 등록 전에 읽어 돌려준다.
        return FaceFeatureResult.from(result.biometricFeature(), result.livenessChecked(), fileService.getFileServerPath(), result.consentEnabled());
    }
}
