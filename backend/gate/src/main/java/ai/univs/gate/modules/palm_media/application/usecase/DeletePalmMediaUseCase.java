package ai.univs.gate.modules.palm_media.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.palm_media.application.input.DeletePalmMediaInput;
import ai.univs.gate.modules.palm_media.domain.entity.PalmMedia;
import ai.univs.gate.modules.palm_media.domain.repository.PalmMediaRepository;
import ai.univs.gate.modules.palm_media.infrastructure.client.dto.DeletePalmFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.palm.PalmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeletePalmMediaUseCase {

    private final PalmMediaRepository palmMediaRepository;
    private final ApiKeyService apiKeyService;
    private final PalmService palmService;

    @Transactional
    public void execute(DeletePalmMediaInput input) {
        PalmMedia palmMedia = palmMediaRepository.findByIdAndIsDeletedFalse(input.palmMediaId())
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();
        if (!palmMedia.getProject().equals(project)) {
            log.error("Not palmMedia who created based on this apikey. accountId: {}, apiKey: {}, palmMediaId: {}",
                    input.accountId(), input.apiKey(), input.palmMediaId());
            throw new CustomGateException(ErrorType.INVALID_USER);
        }

        var deleteRequest = new DeletePalmFeignRequestDTO(
                project.getBranchName(),
                palmMedia.getPalmId(),
                TransactionUtil.useOrCreate(null),
                String.valueOf(input.accountId()));
        palmService.deletePalm(deleteRequest);

        palmMedia.delete();
    }
}
