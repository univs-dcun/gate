package ai.univs.gate.modules.palm_feature.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.palm_feature.application.input.DeletePalmFeatureInput;
import ai.univs.gate.modules.palm_feature.domain.entity.PalmFeature;
import ai.univs.gate.modules.palm_feature.domain.repository.PalmFeatureRepository;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.DeletePalmFeignRequestDTO;
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
public class DeletePalmFeatureUseCase {

    private final PalmFeatureRepository palmFeatureRepository;
    private final ApiKeyService apiKeyService;
    private final PalmService palmService;

    @Transactional
    public void execute(DeletePalmFeatureInput input) {
        PalmFeature palmFeature = palmFeatureRepository.findByIdAndIsDeletedFalse(input.palmFeatureId())
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();
        if (!palmFeature.getProject().equals(project)) {
            log.error("Not palmFeature who created based on this apikey. accountId: {}, apiKey: {}, palmFeatureId: {}",
                    input.accountId(), input.apiKey(), input.palmFeatureId());
            throw new CustomGateException(ErrorType.INVALID_USER);
        }

        var deleteRequest = new DeletePalmFeignRequestDTO(
                project.getBranchName(),
                palmFeature.getFeatureId(),
                TransactionUtil.useOrCreate(null),
                String.valueOf(input.accountId()));
        palmService.deletePalm(deleteRequest);

        palmFeature.delete();
    }
}
