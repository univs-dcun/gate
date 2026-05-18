package ai.univs.gate.modules.match.application.usecase;

import ai.univs.gate.modules.match.application.input.ExtractInput;
import ai.univs.gate.modules.match.application.result.ExtractResult;
import ai.univs.gate.modules.match.infrastructure.client.dto.ExtractFeignRequestDTO;
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
                input.faceImage(),
                input.transactionUuid(),
                input.accountId().toString());

        var response = faceService.extract(feignRequest);
        return new ExtractResult(response.getDescriptor());
    }
}
