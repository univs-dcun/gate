package ai.univs.gate.modules.feature.application.usecase.face;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.face.IdentifyByDescriptorInput;
import ai.univs.gate.modules.feature.application.result.face.IdentifyByDescriptorResult;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.IdentifyFaceByDescriptorFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.MatchFaceFeignResponseDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.face.FaceFeatureService;
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
 * descriptor 기반 1:N 매칭 (UG-279).
 *
 * <p>이미지 기반인 {@link IdentifyFaceUseCase} 와 달라지는 지점.
 * <ul>
 *   <li>{@code FileService} 를 주입하지 않는다. 업로드할 이미지도, 응답에 실을 파일 경로도 없다.</li>
 *   <li>{@code checkLiveness} 를 프로젝트 설정과 무관하게 {@code false} 로 고정한다.</li>
 *   <li>{@code LivenessErrorType} 분기가 없다. 라이브니스를 수행하지 않으므로 face-service 가
 *       라이브니스 계열 오류를 반환할 수 없다. 그 외의 Feign 오류는 이미지 기반과 동일하게
 *       실패 이력을 남긴 뒤 그대로 전파한다.</li>
 *   <li>{@code UseCaseNotifyService} 를 주입하지 않는다 — 웹훅/데모 알림을 발행하지 않는다.
 *       이벤트 이름이 이미지 기반과 같은 "IDENTIFY" 인데 페이로드 필드 수가 다르므로, 기존
 *       IDENTIFY 웹훅 소비자에게 모양이 다른 payload 가 섞여 들어가는 것을 피했다. 신규 API 라
 *       기대치가 아직 없어 지금 빼두는 편이 되돌리기 쉽다.</li>
 * </ul>
 *
 * <p>{@code consentSnapshot} 은 이력에 계속 저장한다 — 응답에서만 제외한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentifyByDescriptorUseCase {

    private final MatchHistoryRepository matchHistoryRepository;
    private final ProjectSettingsService projectSettingsService;
    private final FaceFeatureService faceFeatureService;
    private final ApiKeyService apiKeyService;
    private final FaceService faceService;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = CustomFeignException.class
    )
    public IdentifyByDescriptorResult execute(IdentifyByDescriptorInput input) {
        ApiKey findApiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = findApiKey.getProject();

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(project);

        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.IDENTIFY)
                .featureType(FeatureType.FACE)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(false)
                .success(false)
                .transactionUuid(input.transactionUuid())
                .consentSnapshot(findProjectSettings.getConsentEnabled())
                .build();
        matchHistoryRepository.save(matchHistory);

        var identifyRequest = new IdentifyFaceByDescriptorFeignRequestDTO(
                project.getBranchName(),
                input.descriptor(),
                input.transactionUuid(),
                input.accountId().toString());

        MatchFaceFeignResponseDTO data;
        try {
            data = faceService.identifyByDescriptor(identifyRequest);
        } catch (CustomFeignException e) {
            matchHistory.fail(BigDecimal.ZERO, e.getType());
            throw e;
        }

        if (!data.isResult()) {
            matchHistory.fail(data.getSimilarity(), ErrorType.NOT_MATCH.name());
            return IdentifyByDescriptorResult.failResult(matchHistory);
        }

        BiometricFeature biometricFeature;
        try {
            biometricFeature = faceFeatureService.getFaceFeatureByFaceIdAndProjectId(data.getFaceId(), project.getId());
        } catch (CustomGateException e) {
            matchHistory.fail(BigDecimal.ZERO, e.getErrorType().name());
            return IdentifyByDescriptorResult.failResult(matchHistory);
        }

        matchHistory.success(biometricFeature, data.getSimilarity());

        return IdentifyByDescriptorResult.successResult(matchHistory);
    }
}
