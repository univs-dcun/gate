package ai.univs.gate.modules.face_feature.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.face_feature.application.input.DeleteFaceFeatureInput;
import ai.univs.gate.modules.face_feature.domain.entity.FaceFeature;
import ai.univs.gate.modules.face_feature.domain.repository.FaceFeatureRepository;
import ai.univs.gate.modules.face_feature.infrastructure.client.dto.DeleteFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.face.FaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteFaceFeatureUseCase {

    private final FaceFeatureRepository faceFeatureRepository;
    private final ApiKeyService apiKeyService;
    private final FaceService faceService;

    @Transactional
    public void execute(DeleteFaceFeatureInput input) {
        FaceFeature faceFeature = faceFeatureRepository.findByIdAndIsDeletedFalse(input.faceFeatureId())
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();
        if (!faceFeature.getProject().equals(project)) {
            log.error("Not faceFeature who created based on this apikey. accountId: {}, apiKey: {}, faceFeatureId: {}",
                    input.accountId(), input.apiKey(), input.faceFeatureId());
            throw new CustomGateException(ErrorType.INVALID_USER);
        }

        var deleteRequest = new DeleteFeignRequestDTO(
                project.getBranchName(),
                faceFeature.getFeatureId(),
                TransactionUtil.useOrCreate(null),
                String.valueOf(input.accountId()));
        faceService.deleteFace(deleteRequest);

        faceFeature.delete();
    }
}
