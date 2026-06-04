package ai.univs.gate.modules.match.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.face_media.domain.enums.MediaType;
import ai.univs.gate.modules.match.application.input.PalmIdentifyInput;
import ai.univs.gate.modules.match.application.result.PalmIdentifyResult;
import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.match.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.palm_media.domain.entity.PalmMedia;
import ai.univs.gate.modules.palm_media.infrastructure.client.dto.IdentifyPalmFeignRequestDTO;
import ai.univs.gate.modules.palm_media.infrastructure.client.dto.IdentifyPalmFeignResponseDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.palm.PalmService;
import ai.univs.gate.support.palm_media.PalmMediaService;
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
public class PalmIdentifyUseCase {

    private final MatchHistoryRepository matchHistoryRepository;
    private final ProjectSettingsService projectSettingsService;
    private final ProjectService projectService;
    private final PalmMediaService palmMediaService;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final PalmService palmService;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = CustomFeignException.class
    )
    public PalmIdentifyResult execute(PalmIdentifyInput input) {
        ApiKey findApiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = findApiKey.getProject();

        projectService.validatePalmModuleType(project);

        ProjectSettings projectSettings = projectSettingsService.findByProject(project);

        projectSettingsService.checkAvailabilityModules(input.callerType(), projectSettings);

        boolean consentEnabled = projectSettings.getConsentEnabled();
        var imagePath = fileService.uploadIfConsent(input.palmImage(), consentEnabled);

        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.PALM_IDENTIFY)
                .mediaType(MediaType.PALM)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(projectSettings.getLivenessIdentifyingEnabled())
                .success(false)
                .matchFaceImagePath(imagePath)
                .transactionUuid(input.transactionUuid())
                .consentSnapshot(consentEnabled)
                .build();
        matchHistoryRepository.save(matchHistory);

        var identifyRequest = new IdentifyPalmFeignRequestDTO(
                project.getBranchName(),
                input.palmImage(),
                input.transactionUuid(),
                input.accountId().toString(),
                projectSettings.getLivenessIdentifyingEnabled());

        IdentifyPalmFeignResponseDTO data;
        try {
            data = palmService.identify(identifyRequest);
        } catch (CustomFeignException e) {
            matchHistory.fail(BigDecimal.ZERO, e.getType());
            return PalmIdentifyResult.failResult(matchHistory, e.getType());
        }

        if (!data.isResult()) {
            matchHistory.fail(parseSimilarity(data.getSimilarity()), ErrorType.NOT_MATCH.name());
            return PalmIdentifyResult.failResult(matchHistory, ErrorType.NOT_MATCH.name());
        }

        PalmMedia palmMedia;
        try {
            palmMedia = palmMediaService.getPalmMediaByPalmIdAndProjectId(data.getPalmId(), project.getId());
        } catch (CustomGateException e) {
            matchHistory.fail(BigDecimal.ZERO, e.getErrorType().name());
            return PalmIdentifyResult.failResult(matchHistory, e.getErrorType().name());
        }

        BigDecimal similarity = parseSimilarity(data.getSimilarity());
        matchHistory.success(palmMedia, similarity);

        return PalmIdentifyResult.successResult(matchHistory, palmMedia, similarity, data.getThreshold());
    }

    private BigDecimal parseSimilarity(String similarity) {
        try {
            return new BigDecimal(similarity);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
