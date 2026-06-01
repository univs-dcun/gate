package ai.univs.palm.application.usecase;

import ai.univs.palm.application.input.DeleteInput;
import ai.univs.palm.application.result.DeleteResult;
import ai.univs.palm.domain.ActionType;
import ai.univs.palm.domain.PalmHistory;
import ai.univs.palm.domain.repository.PalmHistoryRepository;
import ai.univs.palm.infrastructure.feign.PalmFeign;
import ai.univs.palm.shared.exception.CustomFeignException;
import ai.univs.palm.shared.exception.InvalidPalmModuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DeleteUseCase {

    private final PalmFeign palmFeign;
    private final PalmHistoryRepository faceHistoryRepository;

    @Transactional(noRollbackFor = InvalidPalmModuleException.class)
    public DeleteResult execute(DeleteInput input) {
        // 삭제 요청 이력 저장
        PalmHistory palmHistory = PalmHistory.create(
                ActionType.REMOVE,
                input.palmId(),
                input.transactionUuid(),
                input.clientId(),
                false);
        faceHistoryRepository.save(palmHistory);

        try {
            // 매처 서버 특징점 삭제(데이터 삭제) — 204 No Content
            palmFeign.delete(input.palmId());

            // 성공 이력 저장
            palmHistory.successDelete(true, input.palmId(), input.clientId());

            return new DeleteResult(
                    input.branchName(),
                    input.palmId(),
                    palmHistory.getTransactionUuid());

        } catch (CustomFeignException e) {
            // 실패 사유
            palmHistory.fail(e.getType(), input.clientId());

            throw new InvalidPalmModuleException(
                    e.getCode(),
                    e.getType(),
                    e.getMessage());
        }
    }
}
