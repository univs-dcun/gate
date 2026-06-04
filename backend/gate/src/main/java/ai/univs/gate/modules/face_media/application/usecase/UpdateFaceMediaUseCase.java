package ai.univs.gate.modules.face_media.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.face_media.application.input.UpdateFaceMediaInput;
import ai.univs.gate.modules.face_media.application.result.FaceMediaResult;
import ai.univs.gate.modules.face_media.domain.entity.FaceMedia;
import ai.univs.gate.modules.face_media.domain.repository.FaceMediaRepository;
import ai.univs.gate.modules.face_media.infrastructure.client.dto.UpdateFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.repository.ProjectSettingsRepository;
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
public class UpdateFaceMediaUseCase {

    private final FaceMediaRepository faceMediaRepository;
    private final FileService fileService;
    private final FaceService faceService;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsRepository projectSettingsRepository;

    @Transactional
    public FaceMediaResult execute(UpdateFaceMediaInput input) {
        FaceMedia faceMedia = faceMediaRepository.findByIdAndIsDeletedFalse(input.faceMediaId())
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();
        if (!faceMedia.getProject().equals(project)) {
            log.error("Not faceMedia who created based on this apikey. accountId: {}, apiKey: {}, faceMediaId: {}",
                    input.accountId(), input.apiKey(), input.faceMediaId());
            throw new CustomGateException(ErrorType.INVALID_USER);
        }

        ProjectSettings projectSettings = projectSettingsRepository.findByProject(project)
                .orElseThrow(() -> new CustomGateException(ErrorType.SETTINGS_NOT_FOUND));

        if (input.hasImage()) {
            input.validationFileExtension();

            String faceImagePath = fileService.uploadIfConsent(input.faceImage(), projectSettings.getConsentEnabled());
            faceMedia.updateFaceImagePath(faceImagePath);

            var updateRequest = new UpdateFeignRequestDTO(
                    project.getBranchName(),
                    faceMedia.getFaceId(),
                    input.faceImage(),
                    input.transactionUuid(),
                    String.valueOf(input.accountId()),
                    projectSettings.getLivenessRegisterEnabled(),
                    projectSettings.getLivenessRegisterEnabled());
            faceService.updateFace(updateRequest);
        }

        faceMedia.updateInfo(input.description(), input.username());

        return FaceMediaResult.from(faceMedia, fileService.getFileServerPath(), projectSettings.getConsentEnabled());
    }
}
