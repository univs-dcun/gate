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
 *
 * <p><b>통과자가 없어도 최근접 유사도는 남긴다</b> (이력·응답 양쪽). 0 으로 눕히면 "아무도
 * 근접하지 않았다" 와 "84.9 로 아깝게 미달했다" 가 구분되지 않는다. 응답에도 실어야 gate 가
 * 자기 이력에 같은 값을 남길 수 있다 — 기존 1:N 은 이미 그렇게 하고 있다.
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

            // 임계치와 무관하게 가장 가까웠던 후보. 통과자가 없을 때 이력에 남길 값이고,
            // gate 도 이 값으로 자기 이력을 남긴다 — 0 으로 눕히면 "아무도 근접하지 않았다" 와
            // "아깝게 미달했다" 가 이력에서 같아 보인다.
            String nearestSimilarity = 최근접_유사도(data);

            // 이력의 대표값은 최상위 후보다. 통과자가 없으면 최근접 값을 대신 남긴다.
            IdentifyCandidatesResult.Candidate top = matched.isEmpty() ? null : matched.getFirst();
            double topSimilarity = similarityParser.parseDoubleSimilarity(
                    top == null
                            ? (nearestSimilarity == null ? "0.0" : nearestSimilarity)
                            : top.similarity());

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
                        input.transactionUuid(), List.of(), nearestSimilarity, thresholdText, false);
            }

            faceMatch.updateFaceId(top.faceId(), input.clientId());
            faceHistory.successMatch(true, input.clientId());

            return new IdentifyCandidatesResult(
                    input.transactionUuid(), matched, nearestSimilarity, thresholdText, true);

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

    /**
     * 임계치와 무관하게 가장 가까웠던 후보의 유사도. 후보가 아예 없으면 {@code null}.
     *
     * <p>match-server 가 유사도 내림차순으로 주므로 첫 원소가 곧 최근접이다.
     */
    private static String 최근접_유사도(IdentifyCandidatesFeignResponseDTO data) {
        return 후보들(data).stream()
                .findFirst()
                .map(IdentifyCandidatesFeignResponseDTO.Candidate::getSimilarity)
                .orElse(null);
    }

    /** match-server 가 candidates 를 null 로 줄 수 있다 — 빈 목록과 같게 다룬다. */
    private static List<IdentifyCandidatesFeignResponseDTO.Candidate> 후보들(
            IdentifyCandidatesFeignResponseDTO data) {
        return data == null || data.getCandidates() == null ? List.of() : data.getCandidates();
    }
}
