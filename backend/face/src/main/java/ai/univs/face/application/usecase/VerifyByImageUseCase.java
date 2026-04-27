package ai.univs.face.application.usecase;

import ai.univs.face.application.input.VerifyByImageInput;
import ai.univs.face.application.result.ExtractResult;
import ai.univs.face.application.result.VerifyByImageResult;
import ai.univs.face.application.service.ExtractService;
import ai.univs.face.application.service.SimilarityParser;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.FaceMatch;
import ai.univs.face.domain.MatchType;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.domain.repository.FaceMatchRepository;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.VerifyByDescriptorFeignRequestDTO;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceImageException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static ai.univs.face.shared.web.enums.ErrorType.NOT_MATCH;

@Component
@RequiredArgsConstructor
public class VerifyByImageUseCase {

    private final MatchFeign matchFeign;
    private final FaceHistoryRepository faceHistoryRepository;
    private final FaceMatchRepository faceMatchRepository;
    private final ExtractService extractService;
    private final SimilarityParser similarityParser;

    @Transactional(noRollbackFor = {
            InvalidFaceImageException.class,
            InvalidFaceModuleException.class,
    })
    public VerifyByImageResult execute(VerifyByImageInput input) {
        // 1:1 확인 요청 이력 저장
        FaceHistory faceHistory = FaceHistory.create(
                ActionType.MATCH,
                "",
                input.transactionUuid(),
                input.clientId(),
                input.checkLiveness(),
                input.checkMultiFace());
        faceHistoryRepository.save(faceHistory);

        // 특징점 추출 요청, 신분증 이미지와 같은 이미지를 대상하므로 라이브니스 제외
        ExtractResult extractResult = extractService.extract(
                faceHistory,
                input.faceImage(),
                input.clientId(),
                false,
                false);

        // 특징점 추출 요청, 라이브니스 대상으로 클라이언트 요청에 따라 적용/미적용
        ExtractResult targetExtractResult = extractService.extract(
                faceHistory,
                input.targetFaceImage(),
                input.clientId(),
                input.checkLiveness(),
                input.checkMultiFace());

        try {
            // 1:1 확인 요청(이미지, 대상 이미지)
            var verifyRequest = new VerifyByDescriptorFeignRequestDTO(
                    extractResult.descriptor(),
                    targetExtractResult.descriptor());
            var verifyResult = matchFeign.verifyByDescriptor(verifyRequest);
            var verifyData = verifyResult.getData();

            // 유사도, 임계치 를 기준으로 동일 인물인지 확인
            double similarity = similarityParser.parseDoubleSimilarity(verifyData.getSimilarity());
            boolean successVerify = similarityParser.isMatchingBySimilarity(similarity);

            // 매칭 결과 저장
            FaceMatch faceMatch = FaceMatch.create(
                    faceHistory,
                    "",
                    similarity,
                    similarityParser.getThreshold(),
                    MatchType.VERIFY_IMAGE,
                    input.clientId());
            faceMatchRepository.save(faceMatch);

            // 유사도가 임계치 보다 낮은 경우
            if (!successVerify) {
                // 실패 이력 저장
                faceHistory.fail(NOT_MATCH.name(), input.clientId());

                // 실패 응답 객체 반환
                return new VerifyByImageResult(
                        input.transactionUuid(),
                        verifyData.getSimilarity(),
                        similarityParser.getThresholdString(),
                        false);
            }
            else {
                // 성공 이력 저장
                faceHistory.successMatch(true, input.clientId());

                // 성공 응답 객체 반환
                return new VerifyByImageResult(
                        input.transactionUuid(),
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
