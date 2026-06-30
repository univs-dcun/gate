package ai.univs.gate.modules.palm_feature.application.usecase;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.palm_feature.application.input.PalmIdentifyInput;
import ai.univs.gate.modules.palm_feature.application.result.PalmIdentifyResult;
import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.match.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.IdentifyPalmFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.IdentifyPalmFeignResponseDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.palm.PalmService;
import ai.univs.gate.support.palm_feature.PalmFeatureService;
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
public class IdentifyPalmUseCase {

    private final MatchHistoryRepository matchHistoryRepository;
    private final ProjectSettingsService projectSettingsService;
    private final ProjectService projectService;
    private final PalmFeatureService palmFeatureService;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final PalmService palmService;
    private final BiometricFeatureRepository biometricFeatureRepository;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = CustomFeignException.class
    )
    public PalmIdentifyResult execute(PalmIdentifyInput input) {
        ApiKey findApiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = findApiKey.getProject();

        ProjectSettings projectSettings = projectSettingsService.findByProject(project);

        boolean consentEnabled = projectSettings.getConsentEnabled();

        // 등록된 팜 사용자가 없으면 사전 차단
        if (biometricFeatureRepository.countByProjectIdAndTypeAndIsDeletedFalse(project.getId(), FeatureType.PALM) == 0) {
            var imagePath = fileService.uploadIfConsent(input.featureImage(), consentEnabled);
            MatchHistory preCheckHistory = MatchHistory.builder()
                    .project(project)
                    .matchType(MatchType.IDENTIFY)
                    .featureType(FeatureType.PALM)
                    .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                    .checkLiveness(projectSettingsService.isLivenessEnabled(projectSettings, FeatureType.PALM, LivenessOperation.IDENTIFY))
                    .success(false)
                    .matchedFeatureImagePath(imagePath)
                    .transactionUuid(input.transactionUuid())
                    .consentSnapshot(consentEnabled)
                    .build();
            matchHistoryRepository.save(preCheckHistory);
            preCheckHistory.fail(BigDecimal.ZERO, "NO_REGISTERED_PALM_USERS");
            return PalmIdentifyResult.failResult(preCheckHistory, "NO_REGISTERED_PALM_USERS",
                    fileService.getFileServerPath(), consentEnabled);
        }

        var imagePath = fileService.uploadIfConsent(input.featureImage(), consentEnabled);

        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.IDENTIFY)
                .featureType(FeatureType.PALM)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(projectSettingsService.isLivenessEnabled(projectSettings, FeatureType.PALM, LivenessOperation.IDENTIFY))
                .success(false)
                .matchedFeatureImagePath(imagePath)
                .transactionUuid(input.transactionUuid())
                .consentSnapshot(consentEnabled)
                .build();
        matchHistoryRepository.save(matchHistory);

        var identifyRequest = new IdentifyPalmFeignRequestDTO(
                project.getBranchName(),
                input.featureImage(),
                input.transactionUuid(),
                input.accountId().toString(),
                projectSettingsService.isLivenessEnabled(projectSettings, FeatureType.PALM, LivenessOperation.IDENTIFY));

        String prefixImagePath = fileService.getFileServerPath();

        IdentifyPalmFeignResponseDTO data;
        try {
            data = palmService.identify(identifyRequest);
        } catch (CustomFeignException e) {
            matchHistory.fail(BigDecimal.ZERO, e.getType());
            return PalmIdentifyResult.failResult(matchHistory, e.getType(), prefixImagePath, consentEnabled);
        }

        if (!data.isResult()) {
            matchHistory.fail(parseSimilarity(data.getSimilarity()), "PALM_NOT_MATCH");
            return PalmIdentifyResult.failResult(matchHistory, "PALM_NOT_MATCH", prefixImagePath, consentEnabled);
        }

        BiometricFeature biometricFeature;
        try {
            biometricFeature = palmFeatureService.getPalmFeatureByPalmIdAndProjectId(data.getFeatureId(), project.getId());
        } catch (CustomGateException e) {
            matchHistory.fail(BigDecimal.ZERO, e.getErrorType().name());
            return PalmIdentifyResult.failResult(matchHistory, e.getErrorType().name(), prefixImagePath, consentEnabled);
        }

        BigDecimal similarity = parseSimilarity(data.getSimilarity());
        matchHistory.success(biometricFeature, similarity);

        return PalmIdentifyResult.successResult(matchHistory, biometricFeature, matchHistory.getSimilarity(), data.getThreshold(), prefixImagePath, consentEnabled);
    }

    private BigDecimal parseSimilarity(String similarity) {
        try {
            // Palm 서비스는 similarity를 퍼센트(0~100)로 반환.
            // MatchHistory.toPercent()가 × 100을 하므로 미리 ÷ 100 처리.
            return new BigDecimal(similarity)
                    .divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
