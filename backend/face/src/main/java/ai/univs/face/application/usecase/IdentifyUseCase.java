package ai.univs.face.application.usecase;

import ai.univs.face.application.input.IdentifyInput;
import ai.univs.face.application.result.ExtractResult;
import ai.univs.face.application.result.IdentifyResult;
import ai.univs.face.application.service.ExtractService;
import ai.univs.face.application.service.FaceHistoryRecorder;
import ai.univs.face.application.service.SimilarityParser;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.FaceMatch;
import ai.univs.face.domain.MatchType;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.IdentifyFeignRequestDTO;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static ai.univs.face.shared.web.enums.ErrorType.NOT_MATCH;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdentifyUseCase {

    private final MatchFeign matchFeign;
    private final FaceHistoryRecorder faceHistoryRecorder;
    private final ExtractService extractService;
    private final SimilarityParser similarityParser;

    /**
     * 트랜잭션이 없다 (UG-358). 이력 시작·결과만 {@link FaceHistoryRecorder} 가 짧게 커밋하고, fxp 추출과 match 호출
     * 동안에는 DB 커넥션을 쥐지 않는다.
     */
    public IdentifyResult execute(IdentifyInput input) {
        // 1:N 매칭 요청 이력 저장 — 먼저 커밋한다
        FaceHistory faceHistory = faceHistoryRecorder.start(FaceHistory.create(
                ActionType.MATCH,
                "",
                input.transactionUuid(),
                input.clientId(),
                input.checkLiveness(),
                input.checkMultiFace()));

        try {
            return identify(input, faceHistory);
        } catch (RuntimeException e) {
            // 어떤 실패든 이력 행을 남긴다 — 예전에는 noRollbackFor 밖의 예외(5xx·타임아웃 등)에 행이 롤백됐다
            faceHistoryRecorder.recordFailure(faceHistory, e, input.clientId());
            throw e;
        }
    }

    private IdentifyResult identify(IdentifyInput input, FaceHistory faceHistory) {
        // 특징점 추출 요청
        ExtractResult extractResult = extractService.extract(
                faceHistory,
                input.faceImage(),
                input.clientId(),
                input.checkLiveness(),
                input.checkMultiFace());

        try {
            // 1:N 매칭 요청
            var identifyRequest = new IdentifyFeignRequestDTO(input.branchName(), extractResult.descriptor());
            var identifyResult = matchFeign.identify(identifyRequest);
            var identifyData = identifyResult.getData();

            // 유사도, 임계치를 기준으로 유효한 인물 체크
            double similarity = similarityParser.parseDoubleSimilarity(identifyData.getSimilarity());
            boolean successIdentify = similarityParser.isMatchingBySimilarity(similarity);
            String faceId = identifyData.getFaceId();

            // 매칭 결과 저장, faceId 의 경우 successIdentify == true 일 때 업데이트
            FaceMatch faceMatch = FaceMatch.create(
                    faceHistory,
                    "",
                    similarity,
                    similarityParser.getThreshold(),
                    MatchType.IDENTIFY,
                    input.clientId());

            // 유사도가 임계치 보다 낮은 경우
            if (!successIdentify) {
                // 실패 이력 저장
                faceHistory.fail(NOT_MATCH.name(), input.clientId());
                faceHistoryRecorder.finish(faceHistory, faceMatch);

                // 실패 결과 반환
                return new IdentifyResult(
                        input.transactionUuid(),
                        "",
                        identifyData.getSimilarity(),
                        similarityParser.getThresholdString(),
                        false);
            }
            else {
                // 사용자 고유 얼굴 아이디 저장
                faceMatch.updateFaceId(faceId, input.clientId());

                // 성공 이력 저장
                faceHistory.successMatch(true, input.clientId());
                faceHistoryRecorder.finish(faceHistory, faceMatch);

                // 성공 결과 반환
                return new IdentifyResult(
                        input.transactionUuid(),
                        faceId,
                        identifyData.getSimilarity(),
                        similarityParser.getThresholdString(),
                        true);
            }
        } catch (CustomFeignException e) {
            // 실패 사유 저장
            faceHistory.fail(e.getType(), input.clientId());

            throw new InvalidFaceModuleException(
                    e.getCode(),
                    e.getType(),
                    e.getMessage());
        }
    }
}
