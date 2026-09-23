package ai.univs.gate.modules.feature.application.usecase.palm;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.palm.PalmLivenessInput;
import ai.univs.gate.modules.feature.application.result.palm.PalmLivenessResult;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.LivenessPalmFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.LivenessPalmFeignResponseDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.history.HistoryRecorder;
import ai.univs.gate.support.feature.palm.PalmService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class LivenessPalmUseCase {

    private final HistoryRecorder historyRecorder;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final PalmService palmService;
    private final ProjectSettingsService projectSettingsService;

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
        matchHistory = historyRecorder.start(matchHistory);

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
            historyRecorder.fail(matchHistory);
            throw e;
        } catch (RemoteCallException e) {
            matchHistory.failUpstream(e);
            historyRecorder.fail(matchHistory);
            throw e;
        }

        // Palm 서비스는 score를 퍼센트(0~100)로 반환.
        // MatchHistory.toPercent()가 × 100을 하므로 미리 ÷ 100 처리.
        BigDecimal score = BigDecimal.valueOf(data.getScore())
                .divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
        if (!data.isSuccess()) {
            matchHistory.fail(score, data.getMessage() != null ? data.getMessage().toUpperCase() : "LIVENESS_FAILED");
            historyRecorder.fail(matchHistory);
        } else {
            matchHistory.success(score);
            historyRecorder.succeed(matchHistory);
        }

        return PalmLivenessResult.from(data, input.transactionUuid());
    }
}
