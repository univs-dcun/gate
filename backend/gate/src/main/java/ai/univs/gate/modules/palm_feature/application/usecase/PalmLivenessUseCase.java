package ai.univs.gate.modules.palm_feature.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.face_feature.domain.enums.FeatureType;
import ai.univs.gate.modules.palm_feature.application.input.PalmLivenessInput;
import ai.univs.gate.modules.palm_feature.application.result.PalmLivenessResult;
import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.match.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.palm_feature.infrastructure.client.dto.LivenessPalmFeignRequestDTO;
import ai.univs.gate.modules.palm_feature.infrastructure.client.dto.LivenessPalmFeignResponseDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.palm.PalmService;
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
public class PalmLivenessUseCase {

    private final MatchHistoryRepository matchHistoryRepository;
    private final ApiKeyService apiKeyService;
    private final ProjectService projectService;
    private final FileService fileService;
    private final PalmService palmService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = CustomFeignException.class
    )
    public PalmLivenessResult execute(PalmLivenessInput input) {
        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
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
        matchHistoryRepository.save(matchHistory);

        var livenessRequest = new LivenessPalmFeignRequestDTO(
                input.featureImage(),
                input.transactionUuid(),
                project.getAccountId().toString());

        LivenessPalmFeignResponseDTO data = palmService.liveness(livenessRequest);

        BigDecimal score = BigDecimal.valueOf(data.getScore());
        if (!data.isSuccess()) {
            matchHistory.fail(score, data.getMessage() != null ? data.getMessage().toUpperCase() : "LIVENESS_FAILED");
        } else {
            matchHistory.success(score);
        }

        return PalmLivenessResult.from(data, input.transactionUuid());
    }
}
