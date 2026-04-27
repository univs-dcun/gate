package ai.univs.gate.modules.user.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.repository.ProjectSettingsRepository;
import ai.univs.gate.modules.user.application.input.UpdateUserInput;
import ai.univs.gate.modules.user.application.result.UserResult;
import ai.univs.gate.modules.user.domain.entity.User;
import ai.univs.gate.modules.user.domain.repository.UserRepository;
import ai.univs.gate.modules.user.infrastructure.client.dto.UpdateFeignRequestDTO;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.face.FaceService;
import ai.univs.gate.support.file.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateUserUseCase {

    private final UserRepository userRepository;
    private final FileService fileService;
    private final FaceService faceService;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsRepository projectSettingsRepository;

    @Transactional
    public UserResult execute(UpdateUserInput input) {
        User user = userRepository.findByIdAndIsDeletedFalse(input.userId())
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();
        if (!user.getProject().equals(project)) {
            log.error("Not user who created based on this apikey. accountId: {}, apyKey: {}, userId: {}",
                    input.accountId(),
                    input.apiKey(),
                    input.userId());
            throw new CustomGateException(ErrorType.INVALID_USER);
        }

        if (input.hasImage()) {
            input.validationFileExtension();

            String faceImagePath = fileService.upload(input.faceImage());
            user.updateFaceImagePath(faceImagePath);

            ProjectSettings projectSettings = projectSettingsRepository.findByProject(project)
                    .orElseThrow(() -> new CustomGateException(ErrorType.SETTINGS_NOT_FOUND));

            var updateUserRequest = new UpdateFeignRequestDTO(
                    project.getBranchName(),
                    input.faceId(),
                    input.faceImage(),
                    input.transactionUuid(),
                    String.valueOf(input.accountId()),
                    projectSettings.getLivenessRecordingEnabled(),
                    // Multi Face
                    projectSettings.getLivenessRecordingEnabled());
            faceService.updateFace(updateUserRequest);
        }

        user.updateUserInfo(input.faceId(), input.description());

        return UserResult.from(user, fileService.getFileServerPath());
    }
}
