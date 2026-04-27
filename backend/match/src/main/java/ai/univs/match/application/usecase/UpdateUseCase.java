package ai.univs.match.application.usecase;

import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.application.input.UpdateInput;
import ai.univs.match.application.result.MatchResult;
import ai.univs.match.application.service.DuplicateService;
import ai.univs.match.domain.entity.Branch;
import ai.univs.match.domain.entity.Descriptor;
import ai.univs.match.infrastructure.persistence.BranchRepository;
import ai.univs.match.infrastructure.persistence.DescriptorRepository;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdateUseCase {

    private final BranchRepository branchRepository;
    private final DescriptorRepository descriptorRepository;
    private final DuplicateService duplicateService;

    @Transactional
    public MatchResult execute(UpdateInput input) {
        DescriptorDetail descriptorDetail = DescriptorDetail.from(input.descriptor());

        Branch branch = branchRepository.findByBranchName(input.branchName())
                .orElseThrow(() -> new CustomFaceMatcherException(ErrorType.EMPTY_GALLERY));

        Descriptor descriptor = descriptorRepository.findByFaceIdAndBranch(input.faceId(), branch)
                .orElseThrow(() -> new CustomFaceMatcherException(ErrorType.INVALID_FACE_ID));

        duplicateService.checkDuplicateDescriptor(branch, descriptorDetail, input.faceId(), true);

        descriptor.updateDescriptor(
                descriptorDetail.descriptor(),
                descriptorDetail.descriptorType(),
                descriptorDetail.descriptorBody()
        );

        return new MatchResult(branch.getBranchName(), descriptor.getFaceId());
    }
}
