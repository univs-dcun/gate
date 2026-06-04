package ai.univs.gate.support.face_media;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.face_media.domain.entity.FaceMedia;
import ai.univs.gate.modules.face_media.domain.enums.MediaType;
import ai.univs.gate.modules.face_media.domain.repository.FaceMediaRepository;
import ai.univs.gate.modules.face_media.infrastructure.client.dto.CreateFeignRequestDTO;
import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.match.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.face.FaceService;
import ai.univs.gate.support.file.FileService;
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
public class FaceMediaService {

    private final FaceMediaRepository faceMediaRepository;
    private final MatchHistoryRepository matchHistoryRepository;
    private final ApiKeyService apiKeyService;
    private final ProjectService projectService;
    private final FileService fileService;
    private final FaceService faceService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = CustomFeignException.class
    )
    public CreateFaceMediaServiceResult createFaceMedia(CallerType callerType,
                                                        Long accountId,
                                                        String apiKey,
                                                        MultipartFile faceImage,
                                                        String description,
                                                        String username,
                                                        String transactionUuid
    ) {
        ApiKey findApiKey = apiKeyService.findByApiKey(apiKey);
        Project project = findApiKey.getProject();

        projectService.validateFaceModuleType(project);

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(project);

        projectSettingsService.checkAvailabilityModules(callerType, findProjectSettings);

        String imagePath = fileService.uploadIfConsent(faceImage, findProjectSettings.getConsentEnabled());

        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.REGISTER)
                .mediaType(MediaType.FACE)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(findProjectSettings.getLivenessRegisterEnabled())
                .success(false)
                .matchFaceImagePath(imagePath)
                .transactionUuid(transactionUuid)
                .consentSnapshot(findProjectSettings.getConsentEnabled())
                .build();
        matchHistoryRepository.save(matchHistory);

        var createRequest = new CreateFeignRequestDTO(
                project.getBranchName(),
                faceImage,
                transactionUuid,
                String.valueOf(accountId),
                findProjectSettings.getLivenessRegisterEnabled(),
                findProjectSettings.getLivenessRegisterEnabled());
        String faceId;
        try {
            faceId = faceService.createFace(createRequest);
        } catch (CustomFeignException e) {
            matchHistory.fail(BigDecimal.ZERO, e.getType());
            throw e;
        }

        FaceMedia faceMedia = FaceMedia.builder()
                .project(project)
                .faceId(faceId)
                .faceImagePath(imagePath)
                .description(description)
                .username(username)
                .isDeleted(false)
                .transactionUuid(transactionUuid)
                .build();
        faceMediaRepository.save(faceMedia);

        matchHistory.success(faceMedia, BigDecimal.ZERO);

        return new CreateFaceMediaServiceResult(faceMedia, findProjectSettings.getLivenessRegisterEnabled());
    }

    public FaceMedia getFaceMediaByFaceIdAndProjectId(String faceId, Long projectId) {
        return faceMediaRepository.findByFaceIdAndProjectIdAndIsDeletedFalse(faceId, projectId)
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));
    }
}
