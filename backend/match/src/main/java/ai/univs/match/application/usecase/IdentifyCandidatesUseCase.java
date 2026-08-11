package ai.univs.match.application.usecase;

import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.application.result.IdentifyCandidatesResult;
import ai.univs.match.application.service.IdentifyService;
import ai.univs.match.domain.entity.Branch;
import ai.univs.match.infrastructure.persistence.BranchRepository;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1:N 후보 목록 매칭 (UG-314).
 *
 * <p>{@link IdentifyUseCase} 와 흐름이 같고 상위 몇 건을 가져오는지만 다르다.
 */
@Component
@RequiredArgsConstructor
public class IdentifyCandidatesUseCase {

    private final BranchRepository branchRepository;
    private final IdentifyService identifyService;

    @Transactional
    public IdentifyCandidatesResult execute(String branchName, String descriptor, int maxCandidates) {
        Branch branch = branchRepository.findByBranchName(branchName)
                .orElseThrow(() -> new CustomFaceMatcherException(ErrorType.EMPTY_GALLERY));

        DescriptorDetail descriptorDetail = DescriptorDetail.from(descriptor);

        return identifyService.identifyCandidates(branch, descriptorDetail, maxCandidates);
    }
}
