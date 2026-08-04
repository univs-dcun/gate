package ai.univs.gate.modules.feature.application.usecase.face;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.face.VerifyByDescriptorInput;
import ai.univs.gate.modules.feature.application.result.face.VerifyByDescriptorResult;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.VerifyFaceByDescriptorFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.face.FaceService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * descriptor 기반 1:1 확인.
 *
 * <p>UG-279: 이 엔드포인트는 그동안 {@link MatchHistory} 를 남기지 않는 유일한 매칭 경로였다.
 * descriptor 계열 전체에 이력을 남기기로 결정하면서 저장을 추가했다. <b>응답 계약은 바꾸지 않았다</b>
 * — 이력은 내부에만 쌓인다.
 *
 * <p>매칭 타입은 {@link MatchType#VERIFY_DESCRIPTOR} 다. VERIFY_ID(촬영)·VERIFY_IMAGE(사진) 는
 * 둘 다 이미지 기반이라 의미상 재사용할 수 없었다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerifyByDescriptorUseCase {

    private final ApiKeyService apiKeyService;
    private final FaceService faceService;
    private final MatchHistoryRepository matchHistoryRepository;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = CustomFeignException.class
    )
    public VerifyByDescriptorResult execute(VerifyByDescriptorInput input) {
        ApiKey findApiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = findApiKey.getProject();

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(project);

        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.VERIFY_DESCRIPTOR)
                .featureType(FeatureType.FACE)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(false)
                .success(false)
                .transactionUuid(input.transactionUuid())
                .consentSnapshot(findProjectSettings.getConsentEnabled())
                .build();
        matchHistoryRepository.save(matchHistory);

        var feignRequest = new VerifyFaceByDescriptorFeignRequestDTO(
                input.descriptor(),
                input.targetDescriptor(),
                input.transactionUuid(),
                input.accountId().toString());

        var response = faceService.verifyDescriptor(feignRequest);

        BigDecimal similarity = toSimilarity(response.getSimilarity());
        if (response.isResult()) {
            // 1:1 확인은 성공해도 등록된 사용자 정보를 특정하지 않는다 (이미지 기반과 동일).
            matchHistory.success(similarity);
        } else {
            matchHistory.fail(similarity, ErrorType.NOT_MATCH.name());
        }

        return new VerifyByDescriptorResult(
                response.getTransactionUuid(),
                response.getSimilarity(),
                response.isResult());
    }

    /**
     * face-service 는 유사도를 문자열("0.85123")로 반환한다. 파싱 실패로 요청 자체를 깨뜨리지
     * 않는다 — 유사도는 이력에만 쓰이는 부가 정보이고, {@code MatchHistory} 는 null 을 허용한다.
     */
    private BigDecimal toSimilarity(String similarity) {
        try {
            return new BigDecimal(similarity);
        } catch (NumberFormatException | NullPointerException e) {
            log.warn("특징점 1:1 확인 유사도를 해석할 수 없어 이력에 남기지 않는다. similarity={}", similarity);
            return null;
        }
    }
}
