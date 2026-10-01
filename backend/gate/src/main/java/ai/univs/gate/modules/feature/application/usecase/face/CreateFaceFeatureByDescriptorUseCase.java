package ai.univs.gate.modules.feature.application.usecase.face;

import ai.univs.gate.modules.feature.application.input.face.CreateFaceFeatureByDescriptorInput;
import ai.univs.gate.modules.feature.application.result.face.FaceFeatureByDescriptorResult;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.support.feature.face.FaceFeatureService;
import ai.univs.gate.support.notify.UseCaseNotifyService;
import ai.univs.gate.support.webhook.WebhookEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * descriptor 기반 특징점 얼굴 등록 (UG-279).
 *
 * <p>이미지 기반인 {@link CreateFaceFeatureUseCase} 와 달리 {@code FileService} 와
 * {@code ProjectSettingsService} 를 주입하지 않는다. 파일 경로와 라이브니스 설정이 응답에 실리지
 * 않으므로 조회할 이유가 없고, 의존성이 없으면 실수로 파일 업로드 경로를 타는 일도 불가능하다.
 */
@Component
@RequiredArgsConstructor
public class CreateFaceFeatureByDescriptorUseCase {

    private final FaceFeatureService faceFeatureService;
    private final UseCaseNotifyService useCaseNotifyService;

    public FaceFeatureByDescriptorResult execute(CreateFaceFeatureByDescriptorInput input) {
        BiometricFeature biometricFeature = faceFeatureService.createFaceFeatureByDescriptor(
                input.accountId(),
                input.apiKey(),
                input.descriptor(),
                input.transactionUuid(),
                input.externalKey());

        FaceFeatureByDescriptorResult result = FaceFeatureByDescriptorResult.from(biometricFeature);
        // UG-345: 이미지 등록과 결과 구조가 달라 이벤트 이름을 나눴다 (WebhookEvent 주석)
        useCaseNotifyService.notifyWebhook(CallerType.API, WebhookEvent.FEATURE_REGISTERED_DESCRIPTOR,
                biometricFeature.getProject().getId(), result.transactionUuid(), result);
        return result;
    }
}
