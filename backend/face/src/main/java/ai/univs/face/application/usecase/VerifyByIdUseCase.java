package ai.univs.face.application.usecase;


import ai.univs.face.application.input.VerifyByIdInput;
import ai.univs.face.application.result.ExtractResult;
import ai.univs.face.application.result.VerifyByIdResult;
import ai.univs.face.application.service.ExtractService;
import ai.univs.face.application.service.FaceHistoryRecorder;
import ai.univs.face.application.service.SimilarityParser;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.FaceMatch;
import ai.univs.face.domain.MatchType;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.VerifyByIdFeignRequestDTO;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static ai.univs.face.shared.web.enums.ErrorType.NOT_MATCH;

@Component
@RequiredArgsConstructor
public class VerifyByIdUseCase {

    private final MatchFeign matchFeign;
    private final FaceHistoryRecorder faceHistoryRecorder;
    private final ExtractService extractService;
    private final SimilarityParser similarityParser;

    /**
     * 트랜잭션이 없다 (UG-358). 이력 시작·결과만 {@link FaceHistoryRecorder} 가 짧게 커밋하고, 원격 호출 동안에는
     * DB 커넥션을 쥐지 않는다.
     */
    public VerifyByIdResult execute(VerifyByIdInput input) {
        // 1:1 확인 요청 이력 저장
        FaceHistory faceHistory = faceHistoryRecorder.start(FaceHistory.create(
                ActionType.MATCH,
                "",
                input.transactionUuid(),
                input.clientId(),
                input.checkLiveness(),
                input.checkMultiFace()));

        try {
            return verify(input, faceHistory);
        } catch (RuntimeException e) {
            // 어떤 실패든 이력 행을 남긴다 — 예전에는 noRollbackFor 밖의 예외(5xx·타임아웃 등)에 행이 롤백됐다
            faceHistoryRecorder.recordFailure(faceHistory, e, input.clientId());
            throw e;
        }
    }

    private VerifyByIdResult verify(VerifyByIdInput input, FaceHistory faceHistory) {

        // 특징점 추출 요청
        ExtractResult extractResult = extractService.extract(
                faceHistory,
                input.faceImage(),
                input.clientId(),
                input.checkLiveness(),
                input.checkMultiFace());

        try {
            // 1:1 확인 요청(faceId, 이미지)
            var verifyRequest = new VerifyByIdFeignRequestDTO(
                    input.branchName(),
                    input.faceId(),
                    extractResult.descriptor());
            var verifyResult = matchFeign.verifyById(verifyRequest);
            var verifyData = verifyResult.getData();

            // 유사도, 임계치 를 기준으로 동일 인물인지 확인
            double similarity = similarityParser.parseDoubleSimilarity(verifyData.getSimilarity());
            boolean successVerify = similarityParser.isMatchingBySimilarity(similarity);

            // 1:1 확인 결과 저장
            FaceMatch faceMatch = FaceMatch.create(
                    faceHistory,
                    input.faceId(),
                    similarity,
                    similarityParser.getThreshold(),
                    MatchType.VERIFY_ID,
                    input.clientId());

            // 유사도가 임계치 보다 낮은 경우
            if (!successVerify) {
                // 실패 이력 저장
                faceHistory.fail(NOT_MATCH.name(), input.clientId());
                faceHistoryRecorder.finish(faceHistory, faceMatch);

                // 실패 응답 객체 반환
                return new VerifyByIdResult(
                        input.transactionUuid(),
                        "",
                        verifyData.getSimilarity(),
                        similarityParser.getThresholdString(),
                        false);
            }
            else {
                // 성공 이력 저장
                faceHistory.successMatch(true, input.clientId());
                faceHistoryRecorder.finish(faceHistory, faceMatch);

                // 성공 응답 객체 반환
                return new VerifyByIdResult(
                        input.transactionUuid(),
                        "",
                        verifyData.getSimilarity(),
                        similarityParser.getThresholdString(),
                        true);
            }
        } catch (CustomFeignException e) {
            // 실패 이력 저장
            faceHistory.fail(e.getType(), input.clientId());

            throw new InvalidFaceModuleException(
                    e.getCode(),
                    e.getType(),
                    e.getMessage());
        }
    }
}
