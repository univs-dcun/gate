package ai.univs.match.application.usecase;

import ai.univs.match.application.result.MatchResult;
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
public class DeleteUseCase {

    private final BranchRepository branchRepository;
    private final DescriptorRepository descriptorRepository;

    @Transactional
    public MatchResult execute(String branchName, String faceId) {
        Branch branch = branchRepository.findByBranchName(branchName)
                .orElseThrow(() -> new CustomFaceMatcherException(ErrorType.EMPTY_GALLERY));

        Descriptor descriptor = descriptorRepository.findByFaceIdAndBranch(faceId, branch)
                .orElseThrow(() -> new CustomFaceMatcherException(ErrorType.INVALID_FACE_ID));

        descriptorRepository.delete(descriptor);

        return new MatchResult(branch.getBranchName(), descriptor.getFaceId());
    }
}
