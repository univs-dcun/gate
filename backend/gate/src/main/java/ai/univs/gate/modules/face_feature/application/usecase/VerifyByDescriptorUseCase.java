package ai.univs.gate.modules.face_feature.application.usecase;

import ai.univs.gate.modules.face_feature.application.input.VerifyByDescriptorInput;
import ai.univs.gate.modules.face_feature.application.result.VerifyByDescriptorResult;
import ai.univs.gate.modules.face_feature.infrastructure.client.dto.VerifyByDescriptorFeignRequestDTO;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.face.FaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerifyByDescriptorUseCase {

    private final ApiKeyService apiKeyService;
    private final FaceService faceService;

    public VerifyByDescriptorResult execute(VerifyByDescriptorInput input) {
        apiKeyService.findByApiKey(input.apiKey());

        var feignRequest = new VerifyByDescriptorFeignRequestDTO(
                input.descriptor(),
                input.targetDescriptor(),
                input.transactionUuid(),
                input.accountId().toString());

        var response = faceService.verifyDescriptor(feignRequest);
        return new VerifyByDescriptorResult(
                response.getTransactionUuid(),
                response.getSimilarity(),
                response.isResult());
    }
}
