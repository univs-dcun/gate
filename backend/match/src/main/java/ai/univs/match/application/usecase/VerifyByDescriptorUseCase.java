package ai.univs.match.application.usecase;

import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.application.result.VerifyResult;
import ai.univs.match.infrastructure.persistence.DescriptorCustomRepository;
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
public class VerifyByDescriptorUseCase {

    private final DescriptorCustomRepository descriptorCustomRepository;

    @Transactional
    public VerifyResult execute(String descriptor, String targetDescriptor) {
        // 얼굴 서비스에서 같은 버전의 특징점을 추출하기 때문에 버전 체크가 필요 없어서 버전 체크 코드 제거.
        DescriptorDetail descriptorDetail = DescriptorDetail.from(descriptor);
        DescriptorDetail targetDescriptorDetail = DescriptorDetail.from(targetDescriptor);

        if (!descriptorDetail.descriptorSpec().equals(targetDescriptorDetail.descriptorSpec())) {
            log.error("The each descriptor is created by other extractor.");
            log.error("descriptor: {}, target descriptor: {}, ",
                    descriptorDetail.descriptorSpec().getVersion(),
                    targetDescriptorDetail.descriptorSpec().getVersion());
            throw new CustomFaceMatcherException(ErrorType.DIFFERENT_EXTRACTION_TYPE);
        }

        // version = 특징점 비교 개수
        Double distance = descriptorCustomRepository.oneToOneMatch(
                descriptorDetail.descriptorBody(),
                targetDescriptorDetail.descriptorBody(),
                512);

        String similarity = getSimilarityByDistance(distance, descriptorDetail.descriptorSpec());

        return new VerifyResult(similarity);
    }
}
