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
     * 트랜잭션을 열지 않는다 (UG-293 반박 리뷰).
     *
     * <p>이 유스케이스는 {@link HistoryRecorder} 밖에서 아무것도 쓰지 않는다. 그런데 초판은
     * {@code @Transactional} 을 남겨 뒀고, 그러면 요청 하나가 <b>커넥션 두 개</b>를 동시에
     * 쥔다 — 바깥 트랜잭션이 조회 시점에 하나를 잡아 끝까지 붙들고, 그 안에서
     * {@code REQUIRES_NEW} 인 이력 기록이 두 번째를 요구한다.
     *
     * <p>리뷰가 풀 크기 1로 재현했다. 기본 풀은 10이고 Tomcat 스레드는 200이므로, 동시 10건이
     * 각자 첫 번째를 쥔 채 두 번째를 기다리면 아무도 진행하지 못하고 전원 타임아웃까지 멈춘다.
     * 바깥 트랜잭션이 face 서비스 호출까지 품고 있어 그 창이 넓다.
     *
     * <p>읽기는 각 리포지토리 호출이 자기 트랜잭션을 연다. 지연 연관은 {@code ApiKeyService} 가
     * 자기 경계 안에서 초기화해 돌려주므로(UG-335) 여기서 트랜잭션이 없어도 읽을 수 있다.
     */
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
            historyRecorder.fail(matchHistory);
            throw e;
        } catch (RemoteCallException e) {
            matchHistory.failUpstream(e);
            historyRecorder.fail(matchHistory);
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
            historyRecorder.fail(matchHistory);
        } else {
            matchHistory.success(livenessScore);
            historyRecorder.succeed(matchHistory);
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
