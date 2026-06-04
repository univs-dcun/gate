package ai.univs.gate.modules.palm_media.application.usecase;

import ai.univs.gate.modules.palm_media.application.input.GetPalmMediaInput;
import ai.univs.gate.modules.palm_media.application.result.PalmMediaResult;
import ai.univs.gate.modules.palm_media.domain.entity.PalmMedia;
import ai.univs.gate.modules.palm_media.domain.repository.PalmMediaRepository;
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
public class GetPalmMediaUseCase {

    private final PalmMediaRepository palmMediaRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional(readOnly = true)
    public PalmMediaResult execute(GetPalmMediaInput input) {
        PalmMedia palmMedia = palmMediaRepository.findByIdAndIsDeletedFalse(input.palmMediaId())
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        var apiKey = apiKeyService.findByApiKey(input.apiKey());
        ProjectSettings settings = projectSettingsService.findByProject(apiKey.getProject());

        return PalmMediaResult.from(palmMedia, fileService.getFileServerPath(), settings.getConsentEnabled());
    }
}
