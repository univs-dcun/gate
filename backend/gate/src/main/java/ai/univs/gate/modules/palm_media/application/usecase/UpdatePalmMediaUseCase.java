package ai.univs.gate.modules.palm_media.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.palm_media.application.input.UpdatePalmMediaInput;
import ai.univs.gate.modules.palm_media.application.result.PalmMediaResult;
import ai.univs.gate.modules.palm_media.domain.entity.PalmMedia;
import ai.univs.gate.modules.palm_media.domain.repository.PalmMediaRepository;
import ai.univs.gate.modules.palm_media.infrastructure.client.dto.DeletePalmFeignRequestDTO;
import ai.univs.gate.modules.palm_media.infrastructure.client.dto.RegisterPalmFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.repository.ProjectSettingsRepository;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.palm.PalmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdatePalmMediaUseCase {

    private final PalmMediaRepository palmMediaRepository;
    private final FileService fileService;
    private final PalmService palmService;
    private final ApiKeyService apiKeyService;
    private final ProjectSettingsRepository projectSettingsRepository;

    @Transactional
    public PalmMediaResult execute(UpdatePalmMediaInput input) {
        PalmMedia palmMedia = palmMediaRepository.findByIdAndIsDeletedFalse(input.palmMediaId())
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();
        if (!palmMedia.getProject().equals(project)) {
            log.error("Not palmMedia who created based on this apikey. accountId: {}, apiKey: {}, palmMediaId: {}",
                    input.accountId(), input.apiKey(), input.palmMediaId());
            throw new CustomGateException(ErrorType.INVALID_USER);
        }

        ProjectSettings projectSettings = projectSettingsRepository.findByProject(project)
                .orElseThrow(() -> new CustomGateException(ErrorType.SETTINGS_NOT_FOUND));

        if (input.palmImage() != null && !input.palmImage().isEmpty()) {
            // palm-service는 update 미지원: 기존 팜 삭제 후 재등록
            palmService.deletePalm(new DeletePalmFeignRequestDTO(
                    project.getBranchName(),
                    palmMedia.getPalmId(),
                    input.transactionUuid(),
                    String.valueOf(input.accountId())));

            String newPalmId = palmService.registerPalm(new RegisterPalmFeignRequestDTO(
                    project.getBranchName(),
                    input.palmImage(),
                    input.transactionUuid(),
                    String.valueOf(input.accountId()),
                    projectSettings.getLivenessRegisterEnabled()));

            String palmImagePath = fileService.uploadIfConsent(input.palmImage(), projectSettings.getConsentEnabled());
            palmMedia.updatePalmImagePath(palmImagePath);
            palmMedia.updatePalmId(newPalmId);
        }

        palmMedia.updateInfo(input.description(), input.username());

        return PalmMediaResult.from(palmMedia, fileService.getFileServerPath(), projectSettings.getConsentEnabled());
    }
}
