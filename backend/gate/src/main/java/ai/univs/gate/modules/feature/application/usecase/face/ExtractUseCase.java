package ai.univs.gate.modules.feature.application.usecase.face;

import ai.univs.gate.modules.feature.application.input.face.ExtractInput;
import ai.univs.gate.modules.feature.application.result.face.ExtractResult;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.ExtractFaceFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
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
        Project project = apiKeyService.findOwnedByApiKey(input.apiKey(), input.accountId()).getProject();

        var feignRequest = new ExtractFaceFeignRequestDTO(
                input.featureImage(),
                input.transactionUuid(),
                // UG-277: 호출자 accountId 가 아니라 프로젝트 소유자 accountId 를 보낸다.
                // 이 값은 face 서비스에서 createdBy·modifiedBy 감사 필드로만 쓰이고 조회 조건에는
                // 쓰이지 않는다(파티셔닝은 branchName). X-Account-Id 가 없으면 null.toString() 으로
                // NPE(500) 가 났고, 라이브니스 두 UseCase 는 원래 이 방식이었다.
                project.getAccountId().toString());

        var response = faceService.extract(feignRequest);
        return new ExtractResult(response.getDescriptor());
    }
}
