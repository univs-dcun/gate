package ai.univs.match.application.service;

import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.application.result.IdentifyCandidateResult;
import ai.univs.match.application.result.IdentifyCandidatesResult;
import ai.univs.match.application.result.IdentifyResult;
import ai.univs.match.domain.entity.Branch;
import ai.univs.match.infrastructure.persistence.DescriptorCustomRepository;
import ai.univs.match.infrastructure.persistence.DescriptorRepository;
import ai.univs.match.infrastructure.persistence.projection.MatchResultProjection;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static ai.univs.match.shared.utils.SimilarityCalculator.getSimilarityByDistance;

@Component
@RequiredArgsConstructor
public class IdentifyService {

    private final DescriptorRepository descriptorRepository;
    private final DescriptorCustomRepository descriptorCustomRepository;

    public IdentifyResult identify(Branch branch, DescriptorDetail descriptorDetail) {
        // 매칭할 대상이 1명 이상있는지 확인
        int descriptorCount = descriptorRepository.countByBranchAndDescriptorVersion(
                branch, descriptorDetail.descriptorSpec().getVersion());

        if (descriptorCount <= 0) throw new CustomFaceMatcherException(ErrorType.EMPTY_GALLERY);

        // version = 특징점 비교 개수
        MatchResultProjection matchResult = descriptorCustomRepository.oneToManyMatch(
                branch.getId(),
                descriptorDetail.descriptorBody(),
                512);

        String similarity = getSimilarityByDistance(matchResult.getDistance(), descriptorDetail.descriptorSpec());

        return new IdentifyResult(matchResult.getFaceId(), similarity);
    }

    /**
     * 유사도가 높은 순으로 최대 {@code maxCandidates} 건 (UG-314).
     *
     * <p>{@link #identify} 와 유사도 계산 경로가 <b>같다.</b> 같은 {@code vlmatch()} 거리에
     * 같은 {@link ai.univs.match.shared.utils.SimilarityCalculator} 를 쓴다 — 두 API 가 같은
     * 대상에 대해 다른 점수를 내면 안 되기 때문이다.
     *
     * <p>임계치로 자르지 않는다. face-service 가 클라이언트 지정값으로 자른다 — 사유는
     * {@link ai.univs.match.infrastructure.persistence.DescriptorCustomRepository#oneToManyMatchTopK}
     * 참고.
     */
    public IdentifyCandidatesResult identifyCandidates(
            Branch branch, DescriptorDetail descriptorDetail, int maxCandidates) {
        int descriptorCount = descriptorRepository.countByBranchAndDescriptorVersion(
                branch, descriptorDetail.descriptorSpec().getVersion());

        if (descriptorCount <= 0) throw new CustomFaceMatcherException(ErrorType.EMPTY_GALLERY);

        List<MatchResultProjection> matches = descriptorCustomRepository.oneToManyMatchTopK(
                branch.getId(),
                descriptorDetail.descriptorBody(),
                512,
                maxCandidates);

        List<IdentifyCandidateResult> candidates = matches.stream()
                .map(match -> new IdentifyCandidateResult(
                        match.getFaceId(),
                        getSimilarityByDistance(match.getDistance(), descriptorDetail.descriptorSpec())))
                .toList();

        return new IdentifyCandidatesResult(candidates);
    }
}
