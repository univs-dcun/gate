package ai.univs.gate.modules.feature.application.usecase.face;

import ai.univs.gate.modules.feature.application.input.face.VerifyByDescriptorInput;
import ai.univs.gate.modules.feature.application.result.face.VerifyByDescriptorResult;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.VerifyFaceByDescriptorFeignRequestDTO;
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

        var feignRequest = new VerifyFaceByDescriptorFeignRequestDTO(
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
