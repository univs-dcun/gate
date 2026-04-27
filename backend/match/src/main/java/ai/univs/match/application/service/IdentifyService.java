package ai.univs.match.application.service;

import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.application.result.IdentifyResult;
import ai.univs.match.domain.entity.Branch;
import ai.univs.match.infrastructure.persistence.DescriptorCustomRepository;
import ai.univs.match.infrastructure.persistence.DescriptorRepository;
import ai.univs.match.infrastructure.persistence.projection.MatchResultProjection;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
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
}
