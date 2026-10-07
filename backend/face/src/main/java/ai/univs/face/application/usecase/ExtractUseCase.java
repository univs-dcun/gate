package ai.univs.face.application.usecase;

import ai.univs.face.application.input.ExtractInput;
import ai.univs.face.application.result.ExtractResult;
import ai.univs.face.application.service.ExtractService;
import ai.univs.face.application.service.FaceHistoryRecorder;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExtractUseCase {

    private final FaceHistoryRecorder faceHistoryRecorder;
    private final ExtractService extractService;

    /**
     * 트랜잭션이 없다 (UG-358 2단계). fxp 추출 동안 DB 커넥션을 쥐지 않는다.
     *
     * <p><b>의도된 변경.</b> 예전 noRollbackFor 는 {@code InvalidFaceImageException} 만 적었는데, 추출 실패
     * (FACE_NOT_FOUND 등)는 {@code ExtractService} 가 {@code InvalidFaceModuleException} 으로 던진다 — 그래서 사유를
     * 적어 놓고도 이력 행이 롤백으로 사라졌다. 이제 시작 행이 먼저 커밋되므로 그 사유와 함께 남는다. 클라이언트가
     * 받는 응답은 같다.
     */
    public ExtractResult execute(ExtractInput input) {
        // 특징점 추출 요청 이력 저장 — 먼저 커밋한다
        FaceHistory faceHistory = faceHistoryRecorder.start(FaceHistory.create(
                ActionType.EXTRACT,
                "",
                input.transactionUuid(),
                input.clientId(),
                false,
                false));

        try {
            // 특징점 추출, 단순 특징점 추출시 라이브니스를 적용하지 않습니다.
            ExtractResult extractResult = extractService.extract(
                    faceHistory,
                    input.faceImage(),
                    input.clientId(),
                    false,
                    false);

            // 특징점 추출 성공 이력 저장
            faceHistory.successExtract(true, input.clientId());
            faceHistoryRecorder.finish(faceHistory, null);

            return new ExtractResult(extractResult.descriptor());
        } catch (RuntimeException e) {
            // 어떤 실패든 이력 행을 남긴다 — 예전에는 추출 실패·5xx·타임아웃 모두 행이 롤백됐다
            faceHistoryRecorder.recordFailure(faceHistory, e, input.clientId());
            throw e;
        }
    }
}
