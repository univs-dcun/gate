package ai.univs.face.application.usecase;

import ai.univs.face.application.input.DeleteInput;
import ai.univs.face.application.result.DeleteResult;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.DeleteFeignRequestDTO;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DeleteUseCase {

    private final MatchFeign matchFeign;
    private final FaceHistoryRepository faceHistoryRepository;

    @Transactional(noRollbackFor = InvalidFaceModuleException.class)
    public DeleteResult execute(DeleteInput input) {
        // 삭제 요청 이력 저장
        FaceHistory faceHistory = FaceHistory.create(
                ActionType.REMOVE,
                input.faceId(),
                input.transactionUuid(),
                input.clientId(),
                false,
                false);
        faceHistoryRepository.save(faceHistory);

        try {
            // 매처 서버 특징점 삭제(데이터 삭제)
            var deleteRequest = new DeleteFeignRequestDTO(input.branchName(), input.faceId());
            var deleteResult = matchFeign.delete(deleteRequest);
            var deleteData = deleteResult.getData();

            // 성공 이력 저장
            faceHistory.successDelete(true, deleteData.getFaceId(), input.clientId());

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
