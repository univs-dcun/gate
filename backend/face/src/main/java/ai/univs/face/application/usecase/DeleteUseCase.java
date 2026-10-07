package ai.univs.face.application.usecase;

import ai.univs.face.application.input.DeleteInput;
import ai.univs.face.application.result.DeleteResult;
import ai.univs.face.application.service.FaceHistoryRecorder;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.DeleteFeignRequestDTO;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeleteUseCase {

    private final MatchFeign matchFeign;
    private final FaceHistoryRecorder faceHistoryRecorder;

    /**
     * 트랜잭션이 없다 (UG-358 2단계). match 삭제 동안 DB 커넥션을 쥐지 않는다.
     *
     * <p>match 삭제가 성공한 뒤 결과 커밋이 실패하면 이력은 실패(INTERNAL_SERVER_ERROR)로 남고 데이터는 이미 없다.
     * 예전에도 같은 창이 있었다(이력은 롤백으로 사라졌다). 클라이언트가 다시 지우면 match 가 {@code INVALID_FACE_ID} 로
     * 답하고 face 는 그것을 그대로 전한다 — gate 의 {@code DownstreamAbsence.이미_없다} 가 그 답을 성공으로 받아
     * 수렴시킨다(UG-338).
     */
    public DeleteResult execute(DeleteInput input) {
        // 삭제 요청 이력 저장 — 먼저 커밋한다
        FaceHistory faceHistory = faceHistoryRecorder.start(FaceHistory.create(
                ActionType.REMOVE,
                input.faceId(),
                input.transactionUuid(),
                input.clientId(),
                false,
                false));

        try {
            return delete(input, faceHistory);
        } catch (RuntimeException e) {
            // 어떤 실패든 이력 행을 남긴다 — 예전에는 noRollbackFor 밖의 예외(5xx·타임아웃·match 혼잡)에 행이 롤백됐다
            faceHistoryRecorder.recordFailure(faceHistory, e, input.clientId());
            throw e;
        }
    }

    private DeleteResult delete(DeleteInput input, FaceHistory faceHistory) {
        try {
            // 매처 서버 특징점 삭제(데이터 삭제)
            var deleteRequest = new DeleteFeignRequestDTO(input.branchName(), input.faceId());
            var deleteResult = matchFeign.delete(deleteRequest);
            var deleteData = deleteResult.getData();

            // 성공 이력 저장
            faceHistory.successDelete(true, deleteData.getFaceId(), input.clientId());
            faceHistoryRecorder.finish(faceHistory, null);

            return new DeleteResult(
                    deleteData.getBranchName(),
                    deleteData.getFaceId(),
                    faceHistory.getTransactionUuid());

        } catch (CustomFeignException e) {
            // 실패 사유
            faceHistory.fail(e.getType(), input.clientId());

            throw new InvalidFaceModuleException(
                    e.getCode(),
                    e.getType(),
                    e.getMessage());
        }
    }
}
