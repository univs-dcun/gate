package ai.univs.gate.modules.face_media.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.face_media.application.input.GetFaceMediaInput;
import ai.univs.gate.modules.face_media.application.result.FaceMediaResult;
import ai.univs.gate.modules.face_media.domain.entity.FaceMedia;
import ai.univs.gate.modules.face_media.domain.repository.FaceMediaRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetFaceMediaUseCase {

    private final FaceMediaRepository faceMediaRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(readOnly = true)
    public FaceMediaResult execute(GetFaceMediaInput input) {
        FaceMedia faceMedia = faceMediaRepository.findByIdAndIsDeletedFalse(input.faceMediaId())
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();
        if (!faceMedia.getProject().equals(project)) {
            log.error("Not faceMedia who created based on this apikey. accountId: {}, apiKey: {}, faceMediaId: {}",
                    input.accountId(), input.apiKey(), input.faceMediaId());
            throw new CustomGateException(ErrorType.INVALID_USER);
        }

        ProjectSettings projectSettings = projectSettingsService.findByProject(project);
        return FaceMediaResult.from(faceMedia, fileService.getFileServerPath(), projectSettings.getConsentEnabled());
    }
}
