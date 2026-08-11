package ai.univs.face.application.usecase;

import ai.univs.face.application.input.IdentifyCandidatesByDescriptorInput;
import ai.univs.face.application.result.IdentifyCandidatesResult;
import ai.univs.face.application.service.SimilarityParser;
import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.FaceMatch;
import ai.univs.face.domain.MatchType;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.domain.repository.FaceMatchRepository;
import ai.univs.face.infrastructure.feign.match.MatchFeign;
import ai.univs.face.infrastructure.feign.match.dto.IdentifyCandidatesFeignRequestDTO;
import ai.univs.face.infrastructure.feign.match.dto.IdentifyCandidatesFeignResponseDTO;
import ai.univs.face.shared.exception.CustomFeignException;
import ai.univs.face.shared.exception.InvalidFaceImageException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static ai.univs.face.shared.web.enums.ErrorType.NOT_MATCH;

/**
 * 특징점 기반 1:N <b>후보 목록</b> 매칭 (UG-314).
 *
 * <p>{@link IdentifyByDescriptorUseCase} 와 두 가지가 다르다.
 *
 * <ul>
 *   <li><b>임계치를 클라이언트가 정한다.</b> 기존 경로는 {@code face.match.threshold} 서버
 *       설정값을 쓴다. 여기서는 요청값을 쓰되 비교 자체는 같은 {@link SimilarityParser} 를 거친다
 *       — 두 API 가 같은 유사도에 다른 판정을 내면 안 된다.</li>
 *   <li><b>결과가 목록이다.</b> match-server 가 유사도 상위 {@code maxCandidates} 건을 주고,
 *       여기서 임계치 미달을 잘라낸다.</li>
 * </ul>
 *
 * <p><b>왜 자르기만 하면 되는가.</b> 유사도 변환이 Platt scaling 이고 지원 버전 모두 계수 A 가
 * 양수라 거리에 대해 단조 감소다. 상위 k건 중 미달자가 있으면 k+1등부터는 거리가 더 머니 반드시
 * 미달이다 — 더 가져와 채울 대상이 존재하지 않는다.
 *
 * <p><b>이력은 요청 하나에 한 행이다</b> (패턴 A). {@link FaceMatch} 는 "요청 하나 = faceId 하나
 * + similarity 하나" 를 전제하므로 후보를 N행으로 넣으면 기존 조회·통계가 깨진다. 대표값으로
 * <b>최상위 후보</b>를 남기고, 후보 목록 전체는 응답에만 둔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentifyCandidatesByDescriptorUseCase {

    private final MatchFeign matchFeign;
    private final FaceHistoryRepository faceHistoryRepository;
    private final FaceMatchRepository faceMatchRepository;
    private final SimilarityParser similarityParser;

    @Transactional(noRollbackFor = {
            InvalidFaceImageException.class,
            InvalidFaceModuleException.class
    })
    public IdentifyCandidatesResult execute(IdentifyCandidatesByDescriptorInput input) {
        // 검사 대상 이미지가 없으므로 두 플래그는 false 고정
        FaceHistory faceHistory = FaceHistory.create(
                ActionType.MATCH,
                "",
                input.transactionUuid(),
                input.clientId(),
                false,
                false);
        faceHistoryRepository.save(faceHistory);

        String thresholdText = String.valueOf(input.threshold());

        try {
            var request = new IdentifyCandidatesFeignRequestDTO(
                    input.branchName(), input.descriptor(), input.maxCandidates());
            var data = matchFeign.identifyCandidates(request).getData();

            List<IdentifyCandidatesResult.Candidate> matched = 임계치를_넘는_후보(data, input.threshold());

            // 이력의 대표값은 최상위 후보다. 목록이 비면 최상위 자체가 없으므로 유사도 0 으로 남긴다.
            // 그래야 "후보가 없었다" 와 "유사도를 못 구했다" 가 이력에서 구분되지 않는 일이 없다.
            IdentifyCandidatesResult.Candidate top = matched.isEmpty() ? null : matched.getFirst();
            double topSimilarity = top == null
                    ? 최상위_유사도(data)
                    : similarityParser.parseDoubleSimilarity(top.similarity());

            FaceMatch faceMatch = FaceMatch.create(
                    faceHistory,
                    "",
                    topSimilarity,
                    input.threshold(),
                    MatchType.IDENTIFY,
                    input.clientId());
            faceMatchRepository.save(faceMatch);

            if (top == null) {
                faceHistory.fail(NOT_MATCH.name(), input.clientId());
                return new IdentifyCandidatesResult(
                        input.transactionUuid(), List.of(), thresholdText, false);
            }

            faceMatch.updateFaceId(top.faceId(), input.clientId());
            faceHistory.successMatch(true, input.clientId());

            return new IdentifyCandidatesResult(
                    input.transactionUuid(), matched, thresholdText, true);

        } catch (CustomFeignException e) {
            faceHistory.fail(e.getType(), input.clientId());

            throw new InvalidFaceModuleException(
                    e.getCode(),
                    e.getType(),
                    e.getMessage());
        }
    }

    private List<IdentifyCandidatesResult.Candidate> 임계치를_넘는_후보(
            IdentifyCandidatesFeignResponseDTO data, double threshold) {
        return 후보들(data).stream()
                .filter(candidate -> similarityParser.isMatchingBySimilarity(
                        similarityParser.parseDoubleSimilarity(candidate.getSimilarity()), threshold))
                .map(candidate -> new IdentifyCandidatesResult.Candidate(
                        candidate.getFaceId(), candidate.getSimilarity()))
                .toList();
    }

    /** 임계치를 넘은 후보가 없을 때 이력에 남길 값. 가장 가까웠던 후보의 유사도다. */
    private double 최상위_유사도(IdentifyCandidatesFeignResponseDTO data) {
        return 후보들(data).stream()
                .findFirst()
                .map(candidate -> similarityParser.parseDoubleSimilarity(candidate.getSimilarity()))
                .orElse(0.0);
    }

    /** match-server 가 candidates 를 null 로 줄 수 있다 — 빈 목록과 같게 다룬다. */
    private static List<IdentifyCandidatesFeignResponseDTO.Candidate> 후보들(
            IdentifyCandidatesFeignResponseDTO data) {
        return data == null || data.getCandidates() == null ? List.of() : data.getCandidates();
    }
}
