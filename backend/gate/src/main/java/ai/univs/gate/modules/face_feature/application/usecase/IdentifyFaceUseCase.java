package ai.univs.gate.modules.face_feature.application.usecase;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.face_feature.application.input.IdentifyInput;
import ai.univs.gate.modules.face_feature.application.result.IdentifyResult;
import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.match.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.face_feature.infrastructure.client.dto.IdentifyFeignRequestDTO;
import ai.univs.gate.modules.face_feature.infrastructure.client.dto.MatchFeignResponseDTO;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdentifyFaceUseCase {

    private final MatchHistoryRepository matchHistoryRepository;
    private final ProjectSettingsService projectSettingsService;
    private final FaceFeatureService faceFeatureService;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final FaceService faceService;
    private final UseCaseNotifyService useCaseNotifyService;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = CustomFeignException.class
    )
    public IdentifyResult execute(IdentifyInput input) {
        ApiKey findApiKey = apiKeyService.findByApiKey(input.apiKey());
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
        matchHistoryRepository.save(matchHistory);

        var identifyRequest = new IdentifyFeignRequestDTO(
                project.getBranchName(),
                input.matchingFeatureImage(),
                input.transactionUuid(),
                input.accountId().toString(),
                projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.FACE, LivenessOperation.IDENTIFY),
                projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.FACE, LivenessOperation.IDENTIFY));

        MatchFeignResponseDTO data;
        try {
            data = faceService.identify(identifyRequest);
        } catch (CustomFeignException e) {
            if (!LivenessErrorType.contains(e.getType())) throw e;

            matchHistory.fail(BigDecimal.ZERO, e.getType());
            return fail(input.callerType(), matchHistory, consentEnabled);
        }

        if (!data.isResult()) {
            matchHistory.fail(data.getSimilarity(), ErrorType.NOT_MATCH.name());
            return fail(input.callerType(), matchHistory, consentEnabled);
        }

        BiometricFeature biometricFeature;
        try {
            biometricFeature = faceFeatureService.getFaceFeatureByFaceIdAndProjectId(data.getFaceId(), project.getId());
        } catch (CustomGateException e) {
            ErrorType errorType = e.getErrorType();
            matchHistory.fail(BigDecimal.ZERO, errorType.name());
            return fail(input.callerType(), matchHistory, consentEnabled);
        }

        matchHistory.success(biometricFeature, data.getSimilarity());

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
