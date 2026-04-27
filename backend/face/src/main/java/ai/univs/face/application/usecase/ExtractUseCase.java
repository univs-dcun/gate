package ai.univs.face.application.usecase;

import ai.univs.face.application.input.ExtractInput;
import ai.univs.face.application.result.ExtractResult;
import ai.univs.face.application.service.ExtractService;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.shared.exception.InvalidFaceImageException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExtractUseCase {

    private final FaceHistoryRepository faceHistoryRepository;
    private final ExtractService extractService;

    @Transactional(noRollbackFor = InvalidFaceImageException.class)
    public ExtractResult execute(ExtractInput input) {
        // 특징점 추출 요청 이력 저장
        FaceHistory faceHistory = FaceHistory.create(
                ActionType.EXTRACT,
                "",
                input.transactionUuid(),
                input.clientId(),
                false,
                false);
        faceHistoryRepository.save(faceHistory);

        // 특징점 추출, 단순 특징점 추출시 라이브니스를 적용하지 않습니다.
        ExtractResult extractResult = extractService.extract(
                faceHistory,
                input.faceImage(),
                input.clientId(),
                false,
                false);

        // 특징점 추출 성공 이력 저장
        faceHistory.successExtract(true, input.clientId());

        return new ExtractResult(extractResult.descriptor());
    }
}
