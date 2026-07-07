package ai.univs.gate.modules.feature.application.usecase.face;

import ai.univs.gate.modules.feature.application.input.face.ExtractInput;
import ai.univs.gate.modules.feature.application.result.face.ExtractResult;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.ExtractFaceFeignRequestDTO;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.face.FaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExtractUseCase {

    private final ApiKeyService apiKeyService;
    private final FaceService faceService;

    public ExtractResult execute(ExtractInput input) {
        apiKeyService.findByApiKey(input.apiKey());

        var feignRequest = new ExtractFaceFeignRequestDTO(
                input.featureImage(),
                input.transactionUuid(),
                input.accountId().toString());

        var response = faceService.extract(feignRequest);
        return new ExtractResult(response.getDescriptor());
    }
}
