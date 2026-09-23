package ai.univs.gate.modules.feature.application.usecase.face;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.face.IdentifyInput;
import ai.univs.gate.modules.feature.application.result.face.IdentifyResult;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.IdentifyFaceFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.MatchFaceFeignResponseDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.shared.web.enums.LivenessErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.history.HistoryRecorder;
import ai.univs.gate.support.feature.face.FaceService;
import ai.univs.gate.support.feature.face.FaceFeatureService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.notify.UseCaseNotifyService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdentifyFaceUseCase {

    private final HistoryRecorder historyRecorder;
    private final ProjectSettingsService projectSettingsService;
    private final FaceFeatureService faceFeatureService;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final FaceService faceService;
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
    public IdentifyResult execute(IdentifyInput input) {
        ApiKey findApiKey = apiKeyService.findByApiKey(input.callerType(), input.apiKey(), input.accountId());
        Project project = findApiKey.getProject();

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(project);

        boolean consentEnabled = findProjectSettings.getConsentEnabled();

        var imagePath = fileService.uploadIfConsent(input.matchingFeatureImage(), consentEnabled);

        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.IDENTIFY)
                .featureType(FeatureType.FACE)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.FACE, LivenessOperation.IDENTIFY))
                .success(false)
                .matchedFeatureImagePath(imagePath)
                .transactionUuid(input.transactionUuid())
                .consentSnapshot(consentEnabled)
                .build();
        matchHistory = historyRecorder.start(matchHistory);

        var identifyRequest = new IdentifyFaceFeignRequestDTO(
                project.getBranchName(),
                input.matchingFeatureImage(),
                input.transactionUuid(),
                // UG-277 반박 리뷰: 여기는 호출자 accountId 를 그대로 보낸다. 프로젝트 소유자로 바꾸면
                // 안 된다 — 무인증 데모 DTO 가 0L 을 넘기고, 그 "0" 이 face/palm 이력에서
                // <b>데모에서 온 행임을 알려주는 유일한 흔적</b>이다. gate 의 MatchHistory 에는
                // callerType·accountId 컬럼이 없고 face/palm 에도 호출자 필드가 없다. 소유자 id 로
                // 통일하면 데모 등록과 인증 등록이 바이트 단위로 같아져 출처를 되찾을 수 없다.
                // 은행권 e-KYC 에서 감사 해상도를 떨어뜨리는 변경이므로 하지 않는다.
                // 인증 경로에서는 소유 검증(ENFORCE)이 호출자 == 소유자를 보장하므로 값이 같다.
                input.accountId().toString(),
                projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.FACE, LivenessOperation.IDENTIFY),
                projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.FACE, LivenessOperation.IDENTIFY));

        MatchFaceFeignResponseDTO data;
        try {
            data = faceService.identify(identifyRequest);
        } catch (CustomFeignException e) {
            // UG-280: 사유를 먼저 남긴다. 예전에는 라이브니스 계열이 아니면 곧바로 rethrow 해서
            // noRollbackFor 로 커밋된 행의 failure_type 이 NULL 로 남았다 — 응답을 받지 못하고
            // 끊긴 요청과 구분되지 않아 이력만 보고는 원인을 알 수 없었다.
            matchHistory.fail(BigDecimal.ZERO, e.getType());
            historyRecorder.fail(matchHistory);
            if (!LivenessErrorType.contains(e.getType())) throw e;

            return fail(input.callerType(), matchHistory, consentEnabled);
        } catch (RemoteCallException e) {
            // UG-280: 하위 서비스 5xx. 예전에는 CustomGateException 이라 noRollbackFor 에
            // 걸리지 않아 트랜잭션이 롤백되고 이 이력 행 자체가 사라졌다.
            matchHistory.failUpstream(e);
            historyRecorder.fail(matchHistory);
            throw e;
        }

        if (!data.isResult()) {
            matchHistory.fail(data.getSimilarity(), ErrorType.NOT_MATCH.name());
            historyRecorder.fail(matchHistory);
            return fail(input.callerType(), matchHistory, consentEnabled);
        }

        BiometricFeature biometricFeature;
        try {
            biometricFeature = faceFeatureService.getFaceFeatureByFaceIdAndProjectId(data.getFaceId(), project.getId());
        } catch (CustomGateException e) {
            ErrorType errorType = e.getErrorType();
            matchHistory.fail(BigDecimal.ZERO, errorType.name());
            historyRecorder.fail(matchHistory);
            return fail(input.callerType(), matchHistory, consentEnabled);
        }

        matchHistory.success(biometricFeature, data.getSimilarity());
        historyRecorder.succeed(matchHistory);

        return success(input.callerType(), matchHistory, consentEnabled);
    }

    private IdentifyResult fail(CallerType callerType, MatchHistory matchHistory, boolean consentEnabled) {
        String prefixImagePath = fileService.getFileServerPath();
        IdentifyResult failResult = IdentifyResult.failResult(matchHistory, prefixImagePath, consentEnabled);

        useCaseNotifyService.notify(
                callerType,
                MatchType.IDENTIFY.name(),
                matchHistory.getProject().getId(),
                matchHistory.getTransactionUuid(),
                failResult);
        return failResult;
    }

    private IdentifyResult success(CallerType callerType, MatchHistory matchHistory, boolean consentEnabled) {
        String prefixImagePath = fileService.getFileServerPath();
        IdentifyResult successResult = IdentifyResult.successResult(matchHistory, prefixImagePath, consentEnabled);

        useCaseNotifyService.notify(
                callerType,
                MatchType.IDENTIFY.name(),
                matchHistory.getProject().getId(),
                matchHistory.getTransactionUuid(),
                successResult);
        return successResult;
    }
}
