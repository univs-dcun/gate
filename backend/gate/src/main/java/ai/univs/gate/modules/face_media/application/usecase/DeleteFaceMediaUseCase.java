package ai.univs.gate.modules.face_media.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.face_media.application.input.DeleteFaceMediaInput;
import ai.univs.gate.modules.face_media.domain.entity.FaceMedia;
import ai.univs.gate.modules.face_media.domain.repository.FaceMediaRepository;
import ai.univs.gate.modules.face_media.infrastructure.client.dto.DeleteFeignRequestDTO;
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
public class DeleteFaceMediaUseCase {

    private final FaceMediaRepository faceMediaRepository;
    private final ApiKeyService apiKeyService;
    private final FaceService faceService;

    @Transactional
    public void execute(DeleteFaceMediaInput input) {
        FaceMedia faceMedia = faceMediaRepository.findByIdAndIsDeletedFalse(input.faceMediaId())
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_USER));

        ApiKey apiKey = apiKeyService.findByApiKey(input.apiKey());
        Project project = apiKey.getProject();
        if (!faceMedia.getProject().equals(project)) {
            log.error("Not faceMedia who created based on this apikey. accountId: {}, apiKey: {}, faceMediaId: {}",
                    input.accountId(), input.apiKey(), input.faceMediaId());
            throw new CustomGateException(ErrorType.INVALID_USER);
        }

        var deleteRequest = new DeleteFeignRequestDTO(
                project.getBranchName(),
                faceMedia.getFaceId(),
                TransactionUtil.useOrCreate(null),
                String.valueOf(input.accountId()));
        faceService.deleteFace(deleteRequest);

        faceMedia.delete();
    }
}
