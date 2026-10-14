package ai.univs.match.application.service;

import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.domain.entity.Branch;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static ai.univs.match.shared.utils.SimilarityCalculator.parseDoubleSimilarity;

@Slf4j
@Component
@RequiredArgsConstructor
public class DuplicateService {

    /** 이 유사도 이상이면 같은 사람으로 보고 등록을 막는다. 커밋 뒤 재검사(UG-374 {@link RecentDuplicateCheck})도 같은 값을 쓴다. */
    public static final double DUPLICATE_SIMILARITY = 0.85;

    private final IdentifyService identifyService;

    // 특징점 중복 체크
    public void checkDuplicateDescriptor(Branch branch,
                                          DescriptorDetail descriptorDetail,
                                          String faceId,
                                          boolean isUpdate
    ) {
        var matchingResult = identifyService.identify(branch, descriptorDetail);

        double similarity = parseDoubleSimilarity(matchingResult.similarity());

        if (similarity >= DUPLICATE_SIMILARITY && (!isUpdate || !matchingResult.faceId().equals(faceId))) {
            // UG-299: 수준 판정은 GlobalExceptionHandler 가 한다. 여기서는 핸들러가
            // 알 수 없는 값만 남긴다 — ERROR 로 두면 한 사건에 ERROR 두 줄이 된다.
            log.warn("이미 등록된 descriptor 다 — similarity={}", similarity);
            // UG-378: 겹친 특징점을 알려 준다 — 콘솔이 「겹친 항목 보기」로 연다
            throw new CustomFaceMatcherException(ErrorType.ALREADY_REGISTERED_DESCRIPTOR,
                    Map.of("faceId", matchingResult.faceId()));
        }
    }
}
