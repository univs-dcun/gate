package ai.univs.match.application.usecase;

import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.application.result.IdentifyResult;
import ai.univs.match.application.service.IdentifyService;
import ai.univs.match.domain.entity.Branch;
import ai.univs.match.infrastructure.persistence.BranchRepository;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class IdentifyUseCase {

    private final BranchRepository branchRepository;
    private final IdentifyService identifyService;

    @Transactional
    public IdentifyResult execute(String branchName, String descriptor) {
        Branch branch = branchRepository.findByBranchName(branchName)
                .orElseThrow(() -> new CustomFaceMatcherException(ErrorType.EMPTY_GALLERY));

        DescriptorDetail descriptorDetail = DescriptorDetail.from(descriptor);

        return identifyService.identify(branch, descriptorDetail);
    }
}
