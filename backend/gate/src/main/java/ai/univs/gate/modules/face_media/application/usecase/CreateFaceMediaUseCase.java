package ai.univs.gate.modules.face_media.application.usecase;

import ai.univs.gate.modules.face_media.application.input.CreateFaceMediaInput;
import ai.univs.gate.modules.face_media.application.result.FaceMediaResult;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.face_media.CreateFaceMediaServiceResult;
import ai.univs.gate.support.face_media.FaceMediaService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateFaceMediaUseCase {

    private final FaceMediaService faceMediaService;
    private final FileService fileService;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional
    public FaceMediaResult execute(CreateFaceMediaInput input) {
        CreateFaceMediaServiceResult result = faceMediaService.createFaceMedia(
                CallerType.API,
                input.accountId(),
                input.apiKey(),
                input.faceImage(),
                input.description(),
                input.username(),
                input.transactionUuid());

        ProjectSettings projectSettings = projectSettingsService.findByProject(
                apiKeyService.findByApiKey(input.apiKey()).getProject());
        return FaceMediaResult.from(result.faceMedia(), result.livenessChecked(), fileService.getFileServerPath(), projectSettings.getConsentEnabled());
    }
}
