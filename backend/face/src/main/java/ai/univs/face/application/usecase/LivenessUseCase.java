package ai.univs.face.application.usecase;

import ai.univs.face.application.input.LivenessInput;
import ai.univs.face.application.result.LivenessResult;
import ai.univs.face.application.service.ExtractService;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LivenessUseCase {

    private final FaceHistoryRepository faceHistoryRepository;
    private final ExtractService extractService;

    @Transactional(noRollbackFor = InvalidFaceModuleException.class)
    public LivenessResult execute(LivenessInput input) {
        // 라이브니스 요청 이력 저장
        FaceHistory faceHistory = FaceHistory.create(
                ActionType.EXTRACT,
                "",
                input.transactionUuid(),
                input.clientId(),
                true,
                true);
        faceHistoryRepository.save(faceHistory);

        // 라이브니스 요청
        LivenessResult livenessResult = extractService.extractForLiveness(
                faceHistory,
                input.faceImage(),
                input.clientId(),
                true,
                true);

        // 라이브니스 요청 성공 이력 저장
        faceHistory.successLiveness(true, input.clientId());

        return livenessResult;
    }
}
