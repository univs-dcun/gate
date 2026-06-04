package ai.univs.gate.modules.face_media.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.face_media.application.input.GetFaceMediaByFaceIdInput;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetFaceMediaByFaceIdUseCase {

    private final FaceMediaRepository faceMediaRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(readOnly = true)
    public FaceMediaResult execute(GetFaceMediaByFaceIdInput input) {
        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();
        FaceMedia faceMedia = faceMediaRepository.findByFaceIdAndProjectIdAndIsDeletedFalse(input.faceId(), project.getId())
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        ProjectSettings projectSettings = projectSettingsService.findByProject(project);
        return FaceMediaResult.from(faceMedia, fileService.getFileServerPath(), projectSettings.getConsentEnabled());
    }
}
