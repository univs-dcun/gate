package ai.univs.face.application.usecase;

import ai.univs.face.application.input.UpdateInput;
import ai.univs.face.application.result.ExtractResult;
import ai.univs.face.application.result.UpdateResult;
import ai.univs.face.application.service.ExtractService;
import ai.univs.face.application.service.FaceHistoryRecorder;
import ai.univs.face.application.service.SimilarityParser;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.UpdateFeignRequestDTO;
import ai.univs.face.infrastructure.feign.match.dto.VerifyByIdFeignRequestDTO;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceImageException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static ai.univs.face.shared.web.enums.ErrorType.MISMATCH;

@Component
@RequiredArgsConstructor
public class UpdateUseCase {

    private final MatchFeign matchFeign;
    private final FaceHistoryRecorder faceHistoryRecorder;
    private final ExtractService extractService;
    private final SimilarityParser similarityParser;

    /**
     * 사용자 얼굴 이미지를 기반으로 추출기에서 특징점을 추출하고 매칭 서버에 특징점 변경 요청합니다.
     *
     * <p>트랜잭션이 없다 (UG-358 2단계). fxp 추출·match 확인·match 변경 동안 DB 커넥션을 쥐지 않는다. 동일 인물이
     * 아니면(MISMATCH) 예외로 끝나고, 아래 {@code recordFailure} 가 그 사유를 남긴다 — 예전 noRollbackFor 가 하던 일이다.
     *
     * @param input 사용자 얼굴 특징점 변경 관련 필드들
     * @return 사용자 고유 아이디
     */
    public UpdateResult execute(UpdateInput input) {
        // 수정 요청 이력 저장 — 먼저 커밋한다
        FaceHistory faceHistory = faceHistoryRecorder.start(FaceHistory.create(
                ActionType.UPDATE,
                input.faceId(),
                input.transactionUuid(),
                input.clientId(),
                input.checkLiveness(),
                input.checkMultiFace()));

        try {
            return update(input, faceHistory);
        } catch (RuntimeException e) {
            // 어떤 실패든 이력 행을 남긴다 — 예전에는 noRollbackFor 밖의 예외(5xx·타임아웃·match 혼잡)에 행이 롤백됐다
            faceHistoryRecorder.recordFailure(faceHistory, e, input.clientId());
            throw e;
        }
    }

    private UpdateResult update(UpdateInput input, FaceHistory faceHistory) {
        // 특징점 추출 요청
        ExtractResult extractResult = extractService.extract(
                faceHistory,
                input.faceImage(),
                input.clientId(),
                input.checkLiveness(),
                input.checkMultiFace());

        try {
            // 1:1 확인 요청, faceId(특징점), 이미지(특징점)가 같은 인물인지 확인 후 특징점을 변경하기 위함
            var verifyRequest = new VerifyByIdFeignRequestDTO(
                    input.branchName(),
                    input.faceId(),
                    extractResult.descriptor());
            var verifyResult = matchFeign.verifyById(verifyRequest);
            var verifyData = verifyResult.getData();

            // 유사도, 임계치를 기준으로 동일 인물 체크
            Double verifySimilarity = similarityParser.parseDoubleSimilarity(verifyData.getSimilarity());
            boolean successVerify = similarityParser.isMatchingBySimilarity(verifySimilarity);
            if (!successVerify) {
                // 실패 사유 저장 — 커밋은 execute 의 recordFailure 가 한다
                faceHistory.fail(MISMATCH.name(), input.clientId());

                throw new InvalidFaceImageException(MISMATCH);
            }

            // 동일 인물 확인 후 특징점 변경
            var updateDescriptorRequest = new UpdateFeignRequestDTO(
                    input.branchName(),
                    input.faceId(),
                    extractResult.descriptor());
            var updateResult = matchFeign.update(updateDescriptorRequest);
            var updateData = updateResult.getData();

            // 변경 성공 이력 저장
            faceHistory.successUpdate(true, updateData.getFaceId(), input.clientId());
            faceHistoryRecorder.finish(faceHistory, null);

            return new UpdateResult(
                    updateData.getBranchName(),
                    updateData.getFaceId(),
                    faceHistory.getTransactionUuid());

        } catch (CustomFeignException e) {
            // 변경 실패 이력 저장
            faceHistory.fail(e.getType(), input.clientId());

            throw new InvalidFaceModuleException(
                    e.getCode(),
                    e.getType(),
                    e.getMessage());
        }
    }
}
