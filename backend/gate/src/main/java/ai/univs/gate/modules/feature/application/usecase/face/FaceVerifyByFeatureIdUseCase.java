package ai.univs.gate.modules.feature.application.usecase.face;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.face.VerifyByFaceIdInput;
import ai.univs.gate.modules.feature.application.result.face.VerifyByFaceIdResult;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.MatchFaceFeignResponseDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.VerifyFaceByFaceIdFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.shared.web.enums.LivenessErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.face.FaceFeatureService;
import ai.univs.gate.support.feature.face.FaceService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.notify.UseCaseNotifyService;
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
public class FaceVerifyByFeatureIdUseCase {

    private final MatchHistoryRepository matchHistoryRepository;
    private final FileService fileService;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsService projectSettingsService;
    private final FaceService faceService;
    private final FaceFeatureService faceFeatureService;
    private final UseCaseNotifyService useCaseNotifyService;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            // UG-280: RemoteCallException 이 목록에 있어야 하위 서비스 5xx 에도
            // 매칭 이력 행이 커밋된다. CustomGateException 을 넣지 않는 이유는
            // 그러면 모든 비즈니스 예외에 커밋을 허용해 버리기 때문이다.
            noRollbackFor = {CustomFeignException.class, RemoteCallException.class}
    )
    public VerifyByFaceIdResult execute(VerifyByFaceIdInput input) {
        ApiKey findApiKey = apiKeyService.findByApiKey(input.callerType(), input.apiKey(), input.accountId());
        Project project = findApiKey.getProject();

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(project);

        boolean consentEnabled = findProjectSettings.getConsentEnabled();

        var imagePath = fileService.uploadIfConsent(input.matchingFeatureImage(), consentEnabled);

        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.VERIFY_ID)
                .featureType(FeatureType.FACE)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.FACE, LivenessOperation.VERIFY_ID))
                .success(false)
                .featureId(input.faceId())
                .matchedFeatureImagePath(imagePath)
                .transactionUuid(input.transactionUuid())
                .consentSnapshot(consentEnabled)
                .build();
        matchHistoryRepository.save(matchHistory);

        BiometricFeature biometricFeature;
        try {
            biometricFeature = faceFeatureService.getFaceFeatureByFaceIdAndProjectId(input.faceId(), project.getId());
        } catch (CustomGateException e) {
            ErrorType errorType = e.getErrorType();
            matchHistory.fail(BigDecimal.ZERO, errorType.name());
            return fail(input.callerType(), matchHistory, consentEnabled);
        }
        matchHistory.updateBiometricFeature(biometricFeature);

        var verifyRequest = new VerifyFaceByFaceIdFeignRequestDTO(
                project.getBranchName(),
                biometricFeature.getFeatureId(),
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
                projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.FACE, LivenessOperation.VERIFY_ID),
                projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.FACE, LivenessOperation.VERIFY_ID));

        MatchFaceFeignResponseDTO data;
        try {
            data = faceService.verifyByFaceId(verifyRequest);
        } catch (CustomFeignException e) {
            // UG-280: 사유를 먼저 남긴다. 예전에는 라이브니스 계열이 아니면 곧바로 rethrow 해서
            // noRollbackFor 로 커밋된 행의 failure_type 이 NULL 로 남았다 — 응답을 받지 못하고
            // 끊긴 요청과 구분되지 않아 이력만 보고는 원인을 알 수 없었다.
            matchHistory.fail(BigDecimal.ZERO, e.getType());
            if (!LivenessErrorType.contains(e.getType())) throw e;

            return fail(input.callerType(), matchHistory, consentEnabled);
        } catch (RemoteCallException e) {
            // UG-280: 하위 서비스 5xx. 예전에는 CustomGateException 이라 noRollbackFor 에
            // 걸리지 않아 트랜잭션이 롤백되고 이 이력 행 자체가 사라졌다.
            matchHistory.fail(BigDecimal.ZERO, e.getErrorType().name());
            throw e;
        }

        if (!data.isResult()) {
            matchHistory.fail(data.getSimilarity(), ErrorType.MISMATCH.name());
            return fail(input.callerType(), matchHistory, consentEnabled);
        }

        matchHistory.successById(data.getSimilarity());
        return success(input.callerType(), matchHistory, consentEnabled);
    }

    private VerifyByFaceIdResult fail(CallerType callerType, MatchHistory matchHistory, boolean consentEnabled) {
        String prefixImagePath = fileService.getFileServerPath();
        VerifyByFaceIdResult failResult = VerifyByFaceIdResult.failResult(matchHistory, prefixImagePath, consentEnabled);

        useCaseNotifyService.notify(
                callerType,
                MatchType.VERIFY_ID.name(),
                matchHistory.getProject().getId(),
                matchHistory.getTransactionUuid(),
                failResult);
        return failResult;
    }

    private VerifyByFaceIdResult success(CallerType callerType, MatchHistory matchHistory, boolean consentEnabled) {
        String prefixImagePath = fileService.getFileServerPath();
        VerifyByFaceIdResult successResult = VerifyByFaceIdResult.successResult(matchHistory, prefixImagePath, consentEnabled);

        useCaseNotifyService.notify(
                callerType,
                MatchType.VERIFY_ID.name(),
                matchHistory.getProject().getId(),
                matchHistory.getTransactionUuid(),
                successResult);
        return successResult;
    }
}
