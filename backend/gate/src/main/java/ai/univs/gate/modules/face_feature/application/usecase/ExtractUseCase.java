package ai.univs.gate.modules.face_feature.application.usecase;

import ai.univs.gate.modules.face_feature.application.input.ExtractInput;
import ai.univs.gate.modules.face_feature.application.result.ExtractResult;
import ai.univs.gate.modules.face_feature.infrastructure.client.dto.ExtractFeignRequestDTO;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.face.FaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExtractUseCase {

    private final ApiKeyService apiKeyService;
    private final FaceService faceService;

    public ExtractResult execute(ExtractInput input) {
        apiKeyService.findByApiKey(input.apiKey());

        var feignRequest = new ExtractFeignRequestDTO(
                input.featureImage(),
                input.transactionUuid(),
                input.accountId().toString());

        var response = faceService.extract(feignRequest);
        return new ExtractResult(response.getDescriptor());
    }
}
