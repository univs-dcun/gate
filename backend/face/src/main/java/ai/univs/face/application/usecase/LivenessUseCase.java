package ai.univs.face.application.usecase;

import ai.univs.face.application.input.LivenessInput;
import ai.univs.face.application.result.LivenessResult;
import ai.univs.face.application.service.ExtractService;
import ai.univs.face.application.service.FaceHistoryRecorder;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LivenessUseCase {

    private final FaceHistoryRecorder faceHistoryRecorder;
    private final ExtractService extractService;

    /**
     * 트랜잭션이 없다 (UG-358 2단계). fxp 호출 동안 DB 커넥션을 쥐지 않는다.
     *
     * <p>라이브니스 실패·다중 얼굴은 예외가 아니다 — {@code extractForLiveness} 가 이력에 사유만 적고 결과를 돌려준다.
     * 그 뒤 성공 표시(result=true)와 함께 커밋하는 것이 예전 동작이고, 그대로 둔다(사유 + result=true). 예외로 끝나는
     * 경우(FACE_NOT_FOUND, 5xx·타임아웃)만 {@code recordFailure} 로 간다.
     */
    public LivenessResult execute(LivenessInput input) {
        // 라이브니스 요청 이력 저장 — 먼저 커밋한다
        FaceHistory faceHistory = faceHistoryRecorder.start(FaceHistory.create(
                ActionType.EXTRACT,
                "",
                input.transactionUuid(),
                input.clientId(),
                true,
                true));

        try {
            // 라이브니스 요청
            LivenessResult livenessResult = extractService.extractForLiveness(
                    faceHistory,
                    input.faceImage(),
                    input.clientId(),
                    true,
                    true);

            // 라이브니스 요청 성공 이력 저장
            faceHistory.successLiveness(true, input.clientId());
            faceHistoryRecorder.finish(faceHistory, null);

            return livenessResult;
        } catch (RuntimeException e) {
            // 어떤 실패든 이력 행을 남긴다 — 예전에는 noRollbackFor 밖의 예외(5xx·타임아웃)에 행이 롤백됐다
            faceHistoryRecorder.recordFailure(faceHistory, e, input.clientId());
            throw e;
        }
    }
}
