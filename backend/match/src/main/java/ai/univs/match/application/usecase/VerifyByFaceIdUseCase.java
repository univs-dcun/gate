package ai.univs.match.application.usecase;

import ai.univs.match.application.enums.DescriptorSpec;
import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.application.input.VerifyByFaceIdInput;
import ai.univs.match.application.result.VerifyResult;
import ai.univs.match.domain.entity.Branch;
import ai.univs.match.domain.entity.Descriptor;
import ai.univs.match.infrastructure.persistence.BranchRepository;
import ai.univs.match.infrastructure.persistence.DescriptorCustomRepository;
import ai.univs.match.infrastructure.persistence.DescriptorRepository;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static ai.univs.match.shared.utils.SimilarityCalculator.getSimilarityByDistance;

@Slf4j
@Component
@RequiredArgsConstructor
public class VerifyByFaceIdUseCase {

    private final BranchRepository branchRepository;
    private final DescriptorRepository descriptorRepository;
    private final DescriptorCustomRepository descriptorCustomRepository;

    @Transactional
    public VerifyResult execute(VerifyByFaceIdInput input) {
        Branch branch = branchRepository.findByBranchName(input.branchName())
                .orElseThrow(() -> new CustomFaceMatcherException(ErrorType.EMPTY_GALLERY));

        Descriptor descriptorByFaceId = descriptorRepository.findByFaceIdAndBranch(input.faceId(), branch)
                .orElseThrow(() -> new CustomFaceMatcherException(ErrorType.INVALID_FACE_ID));

        DescriptorSpec targetDescriptorSpec = DescriptorSpec.fromVersion(descriptorByFaceId.getDescriptorVersion());

        DescriptorDetail descriptorDetail = DescriptorDetail.from(input.descriptor());

        if (!descriptorDetail.descriptorSpec().equals(targetDescriptorSpec)) {
            log.error("The each descriptor is created by other extractor.");
            log.error("descriptor: {}, target descriptor: {}, ", descriptorDetail.descriptorSpec().getVersion(), targetDescriptorSpec.getVersion());
            throw new CustomFaceMatcherException(ErrorType.DIFFERENT_EXTRACTION_TYPE);
        }

        // version = 특징점 비교 개수
        Double distance = descriptorCustomRepository.oneToOneMatch(
                descriptorDetail.descriptorBody(),
                descriptorByFaceId.getDescriptorBody(),
                512);

        String similarity = getSimilarityByDistance(distance, descriptorDetail.descriptorSpec());

        return new VerifyResult(similarity);
    }
}
