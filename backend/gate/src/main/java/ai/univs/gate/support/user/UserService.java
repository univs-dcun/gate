package ai.univs.gate.support.user;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.match.domain.entity.MatchHistory;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.modules.match.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.user.domain.entity.User;
import ai.univs.gate.modules.user.domain.repository.UserRepository;
import ai.univs.gate.modules.user.infrastructure.client.dto.CreateFeignRequestDTO;
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
public class UserService {

    private final UserRepository userRepository;
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
    public CreateUserServiceResult createUser(CallerType callerType,
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

        String imagePath = fileService.upload(faceImage);

        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.REGISTER)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(findProjectSettings.getLivenessRecordingEnabled())
                .success(false)
                .matchFaceImagePath(imagePath)
                .transactionUuid(transactionUuid)
                .build();
        matchHistoryRepository.save(matchHistory);

        var createUserRequest = new CreateFeignRequestDTO(
                project.getBranchName(),
                faceImage,
                transactionUuid,
                String.valueOf(accountId),
                findProjectSettings.getLivenessRecordingEnabled(),
                findProjectSettings.getLivenessRecordingEnabled());
        String faceId = faceService.createFace(createUserRequest);

        User user = User.builder()
                .project(project)
                .faceId(faceId)
                .faceImagePath(imagePath)
                .description(description)
                .username(username)
                .isDeleted(false)
                .transactionUuid(transactionUuid)
                .build();
        userRepository.save(user);

        matchHistory.success(user, BigDecimal.ZERO);

        return new CreateUserServiceResult(user, findProjectSettings.getLivenessRecordingEnabled());
    }

    public User getUserByFaceIdAndProjectId(String faceId, Long projectId) {
        return userRepository.findByFaceIdAndProjectIdAndIsDeletedFalse(faceId, projectId)
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));
    }
}
