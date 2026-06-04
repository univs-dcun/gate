package ai.univs.gate.support.palm_media;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.face_media.domain.enums.MediaType;
import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.match.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.palm_media.domain.entity.PalmMedia;
import ai.univs.gate.modules.palm_media.domain.repository.PalmMediaRepository;
import ai.univs.gate.modules.palm_media.infrastructure.client.dto.RegisterPalmFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.palm.PalmService;
import ai.univs.gate.support.project.ProjectService;
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
public class PalmMediaService {

    private final PalmMediaRepository palmMediaRepository;
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
    public CreatePalmMediaServiceResult createPalmMedia(CallerType callerType,
                                                        Long accountId,
                                                        String apiKey,
                                                        MultipartFile palmImage,
                                                        String description,
                                                        String username,
                                                        String transactionUuid
    ) {
        ApiKey findApiKey = apiKeyService.findByApiKey(apiKey);
        Project project = findApiKey.getProject();

        projectService.validatePalmModuleType(project);

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(project);

        projectSettingsService.checkAvailabilityModules(callerType, findProjectSettings);

        String imagePath = fileService.uploadIfConsent(palmImage, findProjectSettings.getConsentEnabled());

        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.PALM_REGISTER)
                .mediaType(MediaType.PALM)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(findProjectSettings.getLivenessRegisterEnabled())
                .success(false)
                .matchFaceImagePath(imagePath)
                .transactionUuid(transactionUuid)
                .consentSnapshot(findProjectSettings.getConsentEnabled())
                .build();
        matchHistoryRepository.save(matchHistory);

        var registerRequest = new RegisterPalmFeignRequestDTO(
                project.getBranchName(),
                palmImage,
                transactionUuid,
                String.valueOf(accountId),
                findProjectSettings.getLivenessRegisterEnabled());

        String palmId;
        try {
            palmId = palmService.registerPalm(registerRequest);
        } catch (CustomFeignException e) {
            matchHistory.fail(BigDecimal.ZERO, e.getType());
            throw e;
        }

        PalmMedia palmMedia = PalmMedia.builder()
                .project(project)
                .palmId(palmId)
                .palmImagePath(imagePath)
                .description(description)
                .username(username)
                .isDeleted(false)
                .transactionUuid(transactionUuid)
                .build();
        palmMediaRepository.save(palmMedia);

        matchHistory.success(palmMedia, BigDecimal.ZERO);

        return new CreatePalmMediaServiceResult(palmMedia, findProjectSettings.getLivenessRegisterEnabled());
    }

    public PalmMedia getPalmMediaByPalmIdAndProjectId(String palmId, Long projectId) {
        return palmMediaRepository.findByPalmIdAndProjectIdAndIsDeletedFalse(palmId, projectId)
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));
    }
}
