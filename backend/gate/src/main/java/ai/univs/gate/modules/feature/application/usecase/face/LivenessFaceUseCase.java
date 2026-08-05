package ai.univs.gate.modules.feature.application.usecase.face;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.face.LivenessInput;
import ai.univs.gate.modules.feature.application.result.face.LivenessResult;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.LivenessFaceFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.LivenessFaceFeignResponseDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.face.FaceService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.notify.UseCaseNotifyService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class LivenessFaceUseCase {

    private final MatchHistoryRepository matchHistoryRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final FaceService faceService;
    private final ProjectSettingsService projectSettingsService;
    private final UseCaseNotifyService useCaseNotifyService;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            // UG-280: RemoteCallException 이 목록에 있어야 하위 서비스 5xx 에도
            // 매칭 이력 행이 커밋된다. CustomGateException 을 넣지 않는 이유는
            // 그러면 모든 비즈니스 예외에 커밋을 허용해 버리기 때문이다.
            noRollbackFor = {CustomFeignException.class, RemoteCallException.class}
    )
    public LivenessResult execute(LivenessInput input) {
        ApiKey apiKey = apiKeyService.findByApiKey(input.callerType(), input.apiKey(), input.accountId());
        Project project = apiKey.getProject();


        ProjectSettings findProjectSettings = projectSettingsService.findByProject(project);


        boolean consentEnabled = findProjectSettings.getConsentEnabled();

        var imagePath = fileService.uploadIfConsent(input.matchingFeatureImage(), consentEnabled);

        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.LIVENESS)
                .featureType(FeatureType.FACE)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(true)
                .success(false)
                .matchedFeatureImagePath(imagePath)
                .transactionUuid(input.transactionUuid())
                .consentSnapshot(consentEnabled)
                .build();
        matchHistoryRepository.save(matchHistory);

        var livenessRequest = new LivenessFaceFeignRequestDTO(
                input.matchingFeatureImage(),
                input.transactionUuid(),
                project.getAccountId().toString());

        // UG-280: 예전에는 이 호출을 감싸지 않았다. 다른 매칭 UseCase 와 달리 catch 가 아예
        // 없어서, 하위 서비스가 4xx 를 내면 사유 없는 행이 남고 5xx 를 내면 행 자체가
        // 사라졌다 — 라이브니스는 단독으로 가장 많이 호출되는 경로인데 장애 흔적이 없었다.
        LivenessFaceFeignResponseDTO data;
        try {
            data = faceService.liveness(livenessRequest);
        } catch (CustomFeignException e) {
            matchHistory.fail(BigDecimal.ZERO, e.getType());
            throw e;
        } catch (RemoteCallException e) {
            matchHistory.fail(BigDecimal.ZERO, e.getErrorType().name());
            throw e;
        }

        BigDecimal livenessScore = StringUtils.hasText(data.getProbability())
                ? new BigDecimal(data.getProbability())
                : BigDecimal.ZERO;
        if (!data.isSuccess()) {
            matchHistory.fail(livenessScore, data.getPrdioctionDesc().toUpperCase());
        } else {
            matchHistory.success(livenessScore);
        }

        var result = LivenessResult.from(data, input.transactionUuid(), findProjectSettings.getConsentEnabled());

        useCaseNotifyService.notify(
                input.callerType(),
                MatchType.LIVENESS.name(),
                matchHistory.getProject().getId(),
                matchHistory.getTransactionUuid(),
                result);

        return result;
    }
}
