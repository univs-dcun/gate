package ai.univs.gate.modules.feature.application.usecase.face;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.face.LivenessInput;
import ai.univs.gate.modules.feature.application.result.face.LivenessResult;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.LivenessFaceFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.LivenessFaceFeignResponseDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.face.FaceService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.history.HistoryRecorder;
import ai.univs.gate.support.notify.UseCaseNotifyService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
@RequiredArgsConstructor
public class LivenessFaceUseCase {

    private final HistoryRecorder historyRecorder;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final FaceService faceService;
    private final ProjectSettingsService projectSettingsService;
    private final UseCaseNotifyService useCaseNotifyService;

    /**
     * UG-293: 이력 커밋이 이 트랜잭션과 분리됐다.
     *
     * <p>예전에는 {@code REQUIRES_NEW + noRollbackFor} 로 "이 예외들에서는 롤백하지 말라" 고
     * 열거했다. 목록에 없는 예외 — 특히 우리 코드의 NPE — 에서는 이력이 그대로 사라졌다.
     * 이제 {@link HistoryRecorder} 가 행을 먼저 커밋하므로 여기서 무엇이 나든 행은 남는다.
     */
    @Transactional
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
        matchHistory = historyRecorder.start(matchHistory);

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
            historyRecorder.finish(matchHistory);
            throw e;
        } catch (RemoteCallException e) {
            matchHistory.failUpstream(e);
            historyRecorder.finish(matchHistory);
            throw e;
        }

        // UG-280 3차 반박 리뷰: 200 응답의 본문 값은 신뢰하지 않는다. 예전에는 여기서 NPE 가
        // 나면 이력 행까지 사라졌다 — UG-293 이후로는 행이 남으므로 이 가드는 '사유를 남긴다'
        // 는 의미만 갖는다. (palm 쪽은 이미 같은 가드가 있었고 face 만 무방비였다.)
        BigDecimal livenessScore = parseProbability(data.getProbability());
        if (!data.isSuccess()) {
            String reason = StringUtils.hasText(data.getPrdioctionDesc())
                    ? data.getPrdioctionDesc().toUpperCase()
                    : "LIVENESS_FAILED";
            matchHistory.fail(livenessScore, reason);
            historyRecorder.finish(matchHistory);
        } else {
            matchHistory.success(livenessScore);
            historyRecorder.finish(matchHistory);
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

    /**
     * probability 는 하위 서비스가 문자열로 주며 숫자 보장이 없다.
     *
     * <p>{@code new BigDecimal(...)} 이 {@code NumberFormatException} 을 내면 이력 행이
     * 롤백된다. 점수를 못 읽는 것과 시도 기록이 통째로 사라지는 것 중에는 전자가 낫다.
     * (같은 형태의 방어가 {@code IdentifyPalmUseCase.parseSimilarity} 와
     * {@code VerifyByDescriptorUseCase.toSimilarity} 에 이미 있다.)
     */
    private BigDecimal parseProbability(String probability) {
        if (!StringUtils.hasText(probability)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(probability);
        } catch (NumberFormatException e) {
            log.warn("라이브니스 probability 를 숫자로 읽지 못했다. value={}", probability);
            return BigDecimal.ZERO;
        }
    }
}
