package ai.univs.face.application.usecase;

import ai.univs.face.application.input.IdentifyByDescriptorInput;
import ai.univs.face.application.result.IdentifyResult;
import ai.univs.face.application.service.SimilarityParser;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.FaceMatch;
import ai.univs.face.domain.MatchType;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.domain.repository.FaceMatchRepository;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.IdentifyFeignRequestDTO;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceImageException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static ai.univs.face.shared.web.enums.ErrorType.NOT_MATCH;

/**
 * descriptor 기반 1:N 매칭 (UG-279).
 *
 * <p>{@link IdentifyUseCase} 와 달리 {@code ExtractService} 를 <b>주입하지 않는다.</b> 사유는
 * {@link RegisterByDescriptorUseCase} 와 같다.
 *
 * <p>매칭 타입은 이미지 기반과 동일하게 {@link MatchType#IDENTIFY} 를 재사용한다. IMAGE/DESCRIPTOR
 * 구분이 실제로 필요해지는 시점에 함께 분리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentifyByDescriptorUseCase {

    private final MatchFeign matchFeign;
    private final FaceHistoryRepository faceHistoryRepository;
    private final FaceMatchRepository faceMatchRepository;
    private final SimilarityParser similarityParser;

    @Transactional(noRollbackFor = {
            InvalidFaceImageException.class,
            InvalidFaceModuleException.class
    })
    public IdentifyResult execute(IdentifyByDescriptorInput input) {
        // 1:N 매칭 요청 이력 저장 — 검사 대상 이미지가 없으므로 두 플래그는 false 고정
        FaceHistory faceHistory = FaceHistory.create(
                ActionType.MATCH,
                "",
                input.transactionUuid(),
                input.clientId(),
                false,
                false);
        faceHistoryRepository.save(faceHistory);

        try {
            // 1:N 매칭 요청
            var identifyRequest = new IdentifyFeignRequestDTO(input.branchName(), input.descriptor());
            var identifyData = matchFeign.identify(identifyRequest).getData();

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
            faceMatchRepository.save(faceMatch);

            // 유사도가 임계치 보다 낮은 경우
            if (!successIdentify) {
                faceHistory.fail(NOT_MATCH.name(), input.clientId());

                return new IdentifyResult(
                        input.transactionUuid(),
                        "",
                        identifyData.getSimilarity(),
                        similarityParser.getThresholdString(),
                        false);
            }

            // 사용자 고유 얼굴 아이디 저장
            faceMatch.updateFaceId(faceId, input.clientId());

            // 성공 이력 저장
            faceHistory.successMatch(true, input.clientId());

            return new IdentifyResult(
                    input.transactionUuid(),
                    faceId,
                    identifyData.getSimilarity(),
                    similarityParser.getThresholdString(),
                    true);

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
