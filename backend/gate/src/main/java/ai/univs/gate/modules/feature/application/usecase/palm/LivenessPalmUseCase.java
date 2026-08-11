package ai.univs.gate.modules.feature.application.usecase.palm;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.palm.PalmLivenessInput;
import ai.univs.gate.modules.feature.application.result.palm.PalmLivenessResult;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.LivenessPalmFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.LivenessPalmFeignResponseDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.palm.PalmService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class LivenessPalmUseCase {

    private final MatchHistoryRepository matchHistoryRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final PalmService palmService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            // UG-280: RemoteCallException 이 목록에 있어야 하위 서비스 5xx 에도
            // 매칭 이력 행이 커밋된다. CustomGateException 을 넣지 않는 이유는
            // 그러면 모든 비즈니스 예외에 커밋을 허용해 버리기 때문이다.
            noRollbackFor = {CustomFeignException.class, RemoteCallException.class}
    )
    public PalmLivenessResult execute(PalmLivenessInput input) {
        ApiKey apiKey = apiKeyService.findByApiKey(input.callerType(), input.apiKey(), input.accountId());
        Project project = apiKey.getProject();


        ProjectSettings projectSettings = projectSettingsService.findByProject(project);


        boolean consentEnabled = projectSettings.getConsentEnabled();
        var imagePath = fileService.uploadIfConsent(input.featureImage(), consentEnabled);

        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.LIVENESS)
                .featureType(FeatureType.PALM)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(true)
                .success(false)
                .matchedFeatureImagePath(imagePath)
                .transactionUuid(input.transactionUuid())
                .consentSnapshot(consentEnabled)
                .build();
        matchHistoryRepository.save(matchHistory);

        var livenessRequest = new LivenessPalmFeignRequestDTO(
                input.featureImage(),
                input.transactionUuid(),
                project.getAccountId().toString());

        // UG-280: LivenessFaceUseCase 와 같은 이유로 감싼다. 감싸지 않으면 하위 서비스
        // 4xx 에는 사유 없는 행이, 5xx 에는 행 자체가 남지 않는다.
        LivenessPalmFeignResponseDTO data;
        try {
            data = palmService.liveness(livenessRequest);
        } catch (CustomFeignException e) {
            matchHistory.fail(BigDecimal.ZERO, e.getType());
            throw e;
        } catch (RemoteCallException e) {
            matchHistory.fail(BigDecimal.ZERO, e.getErrorType().name());
            throw e;
        }

        // Palm 서비스는 score를 퍼센트(0~100)로 반환.
        // MatchHistory.toPercent()가 × 100을 하므로 미리 ÷ 100 처리.
        BigDecimal score = BigDecimal.valueOf(data.getScore())
                .divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
        if (!data.isSuccess()) {
            matchHistory.fail(score, data.getMessage() != null ? data.getMessage().toUpperCase() : "LIVENESS_FAILED");
        } else {
            matchHistory.success(score);
        }

        return PalmLivenessResult.from(data, input.transactionUuid());
    }
}
