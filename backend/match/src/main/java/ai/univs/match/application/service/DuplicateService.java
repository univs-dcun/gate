package ai.univs.match.application.service;

import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.domain.entity.Branch;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static ai.univs.match.shared.utils.SimilarityCalculator.parseDoubleSimilarity;

@Slf4j
@Component
@RequiredArgsConstructor
public class DuplicateService {

    private final IdentifyService identifyService;

    // 특징점 중복 체크
    public void checkDuplicateDescriptor(Branch branch,
                                          DescriptorDetail descriptorDetail,
                                          String faceId,
                                          boolean isUpdate
    ) {
        var matchingResult = identifyService.identify(branch, descriptorDetail);

        double similarity = parseDoubleSimilarity(matchingResult.similarity());

        if (similarity >= 0.85 && (!isUpdate || !matchingResult.faceId().equals(faceId))) {
            log.error("ALREADY_REGISTERED_DESCRIPTOR. similarity: {}", similarity);
            throw new CustomFaceMatcherException(ErrorType.ALREADY_REGISTERED_DESCRIPTOR);
        }
    }
}
