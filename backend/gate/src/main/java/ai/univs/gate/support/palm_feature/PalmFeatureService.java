package ai.univs.gate.support.palm_feature;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.match.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.RegisterPalmFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.palm.PalmService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class PalmFeatureService {

    private final BiometricFeatureRepository biometricFeatureRepository;
    private final MatchHistoryRepository matchHistoryRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final PalmService palmService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = CustomFeignException.class
    )
    public CreatePalmFeatureServiceResult createPalmFeature(Long accountId,
                                                            String apiKey,
                                                            MultipartFile featureImage,
                                                            String description,
                                                            String transactionUuid
    ) {
        ApiKey findApiKey = apiKeyService.findByApiKey(apiKey);
        Project project = findApiKey.getProject();

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(project);

        String imagePath = fileService.uploadIfConsent(featureImage, findProjectSettings.getConsentEnabled());

        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.REGISTER)
                .featureType(FeatureType.PALM)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.PALM, LivenessOperation.REGISTER))
                .success(false)
                .matchedFeatureImagePath(imagePath)
                .transactionUuid(transactionUuid)
                .consentSnapshot(findProjectSettings.getConsentEnabled())
                .build();
        matchHistoryRepository.save(matchHistory);

        var registerRequest = new RegisterPalmFeignRequestDTO(
                project.getBranchName(),
                featureImage,
                transactionUuid,
                String.valueOf(accountId),
                projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.PALM, LivenessOperation.REGISTER));

        String palmId;
        try {
            palmId = palmService.registerPalm(registerRequest);
        } catch (CustomFeignException e) {
            matchHistory.fail(BigDecimal.ZERO, e.getType());
            throw e;
        }

        BiometricFeature biometricFeature = BiometricFeature.builder()
                .project(project)
                .type(FeatureType.PALM)
                .featureId(palmId)
                .featureImagePath(imagePath)
                .description(description)
                .isDeleted(false)
                .transactionUuid(transactionUuid)
                .build();
        biometricFeatureRepository.save(biometricFeature);

        matchHistory.success(biometricFeature, BigDecimal.ZERO);

        return new CreatePalmFeatureServiceResult(biometricFeature, projectSettingsService.isLivenessEnabled(findProjectSettings, FeatureType.PALM, LivenessOperation.REGISTER));
    }

    public BiometricFeature getPalmFeatureByPalmIdAndProjectId(String featureId, Long projectId) {
        return biometricFeatureRepository.findByFeatureIdAndProjectIdAndTypeAndIsDeletedFalse(featureId, projectId, FeatureType.PALM)
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));
    }
}
