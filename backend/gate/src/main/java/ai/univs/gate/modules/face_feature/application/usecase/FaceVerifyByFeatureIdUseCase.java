package ai.univs.gate.modules.face_feature.application.usecase;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.face_feature.application.input.VerifyByFaceIdInput;
import ai.univs.gate.modules.face_feature.application.result.VerifyByFaceIdResult;
import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.match.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.face_feature.infrastructure.client.dto.MatchFeignResponseDTO;
import ai.univs.gate.modules.face_feature.infrastructure.client.dto.VerifyByFaceIdFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.shared.web.enums.LivenessErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.face.FaceService;
import ai.univs.gate.support.face_feature.FaceFeatureService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.notify.UseCaseNotifyService;
import ai.univs.gate.support.project.ProjectService;
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
    private final ProjectService projectService;
    private final FaceService faceService;
    private final FaceFeatureService faceFeatureService;
    private final UseCaseNotifyService useCaseNotifyService;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = CustomFeignException.class
    )
    public VerifyByFaceIdResult execute(VerifyByFaceIdInput input) {
        ApiKey findApiKey = apiKeyService.findByApiKey(input.apiKey());
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

        var verifyRequest = new VerifyByFaceIdFeignRequestDTO(
                project.getBranchName(),
                biometricFeature.getFeatureId(),
                input.matchingFeatureImage(),
                input.transactionUuid(),
                input.accountId().toString(),
                projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.FACE, LivenessOperation.VERIFY_ID),
                projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.FACE, LivenessOperation.VERIFY_ID));

        MatchFeignResponseDTO data;
        try {
            data = faceService.verifyByFaceId(verifyRequest);
        } catch (CustomFeignException e) {
            if (!LivenessErrorType.contains(e.getType())) throw e;

            matchHistory.fail(BigDecimal.ZERO, e.getType());
            return fail(input.callerType(), matchHistory, consentEnabled);
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
