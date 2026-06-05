package ai.univs.gate.support.face_feature;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.face_feature.domain.entity.FaceFeature;
import ai.univs.gate.modules.face_feature.domain.enums.FeatureType;
import ai.univs.gate.modules.face_feature.domain.repository.FaceFeatureRepository;
import ai.univs.gate.modules.face_feature.infrastructure.client.dto.CreateFeignRequestDTO;
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
public class FaceFeatureService {

    private final FaceFeatureRepository faceFeatureRepository;
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
    public CreateFaceFeatureServiceResult createFaceFeature(CallerType callerType,
                                                        Long accountId,
                                                        String apiKey,
                                                        MultipartFile featureImage,
                                                        String description,
                                                        String username,
                                                        String transactionUuid
    ) {
        ApiKey findApiKey = apiKeyService.findByApiKey(apiKey);
        Project project = findApiKey.getProject();

        projectService.validateFaceModuleType(project);

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(project);

        projectSettingsService.checkAvailabilityModules(callerType, findProjectSettings);

        String imagePath = fileService.uploadIfConsent(featureImage, findProjectSettings.getConsentEnabled());

        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.REGISTER)
                .featureType(FeatureType.FACE)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(findProjectSettings.getLivenessRegisterEnabled())
                .success(false)
                .matchedFeatureImagePath(imagePath)
                .transactionUuid(transactionUuid)
                .consentSnapshot(findProjectSettings.getConsentEnabled())
                .build();
        matchHistoryRepository.save(matchHistory);

        var createRequest = new CreateFeignRequestDTO(
                project.getBranchName(),
                featureImage,
                transactionUuid,
                String.valueOf(accountId),
                findProjectSettings.getLivenessRegisterEnabled(),
                findProjectSettings.getLivenessRegisterEnabled());
        String featureId;
        try {
            featureId = faceService.createFace(createRequest);
        } catch (CustomFeignException e) {
            matchHistory.fail(BigDecimal.ZERO, e.getType());
            throw e;
        }

        FaceFeature faceFeature = FaceFeature.builder()
                .project(project)
                .featureId(featureId)
                .featureImagePath(imagePath)
                .description(description)
                .username(username)
                .isDeleted(false)
                .transactionUuid(transactionUuid)
                .build();
        faceFeatureRepository.save(faceFeature);

        matchHistory.success(faceFeature, BigDecimal.ZERO);

        return new CreateFaceFeatureServiceResult(faceFeature, findProjectSettings.getLivenessRegisterEnabled());
    }

    public FaceFeature getFaceFeatureByFaceIdAndProjectId(String featureId, Long projectId) {
        return faceFeatureRepository.findByFeatureIdAndProjectIdAndIsDeletedFalse(featureId, projectId)
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));
    }
}
