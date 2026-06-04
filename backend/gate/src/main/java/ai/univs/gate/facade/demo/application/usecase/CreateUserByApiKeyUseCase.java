package ai.univs.gate.facade.demo.application.usecase;

import ai.univs.gate.facade.demo.application.input.CreateUserByApiKeyInput;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
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
public class CreateUserByApiKeyUseCase {

    private final FaceMediaService faceMediaService;
    private final FileService fileService;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional
    public FaceMediaResult execute(CreateUserByApiKeyInput input) {
        ApiKey findApiKey = apiKeyService.findByApiKey(input.apiKey());

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(findApiKey.getProject());

        // 데모 활성화 여부 체크
        projectSettingsService.validateDemoEnabled(findProjectSettings);

        CreateFaceMediaServiceResult result = faceMediaService.createFaceMedia(
                CallerType.DEMO,
                input.accountId(),
                input.apiKey(),
                input.faceImage(),
                input.description(),
                input.username(),
                input.transactionUuid());
        return FaceMediaResult.from(result.faceMedia(), result.livenessChecked(), fileService.getFileServerPath(), findProjectSettings.getConsentEnabled());
    }
}
