package ai.univs.gate.modules.feature.application.usecase.face;

import ai.univs.gate.modules.feature.application.result.face.FaceFeatureResult;
import ai.univs.gate.modules.feature.application.input.CreateFeatureInput;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.support.feature.face.CreateFaceFeatureServiceResult;
import ai.univs.gate.support.feature.face.FaceFeatureService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ai.univs.gate.shared.web.enums.CallerType;

@Component
@RequiredArgsConstructor
public class CreateFaceFeatureUseCase {

    private final FaceFeatureService faceFeatureService;
    private final FileService fileService;
    private final ProjectSettingsService projectSettingsService;

    @Transactional
    public FaceFeatureResult execute(CreateFeatureInput input) {
        CreateFaceFeatureServiceResult result = faceFeatureService.createFaceFeature(
                CallerType.API,
                input.accountId(),
                input.apiKey(),
                input.featureImage(),
                input.description(),
                input.transactionUuid());

        // UG-281 반박 리뷰: 여기서 API 키를 다시 조회하지 않는다. 소유 검증은 위 서비스가
        // 맨 앞에서 이미 마쳤고, 이 두 번째 조회는 REQUIRES_NEW 커밋 '이후' 바깥 트랜잭션에서
        // 도는 탓에 두 호출 사이에 mode 가 바뀌면 특징점은 남은 채 400 이 나가는, 이 티켓이
        // 없앤 고아 창을 좁게 되살렸다. 서비스가 돌려준 특징점의 프로젝트를 그대로 쓴다.
        ProjectSettings projectSettings = projectSettingsService.findByProject(
                result.biometricFeature().getProject());
        return FaceFeatureResult.from(result.biometricFeature(), result.livenessChecked(), fileService.getFileServerPath(), projectSettings.getConsentEnabled());
    }
}
