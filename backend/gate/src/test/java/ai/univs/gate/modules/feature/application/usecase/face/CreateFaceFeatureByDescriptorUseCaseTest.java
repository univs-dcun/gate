package ai.univs.gate.modules.feature.application.usecase.face;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ai.univs.gate.modules.feature.application.input.face.CreateFaceFeatureByDescriptorInput;
import ai.univs.gate.modules.feature.application.result.face.FaceFeatureByDescriptorResult;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.support.feature.face.FaceFeatureService;
import ai.univs.gate.support.notify.UseCaseNotifyService;
import ai.univs.gate.support.webhook.WebhookEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UG-345: descriptor 등록 웹훅")
class CreateFaceFeatureByDescriptorUseCaseTest {

    @Mock private FaceFeatureService faceFeatureService;
    @Mock private UseCaseNotifyService useCaseNotifyService;

    @InjectMocks private CreateFaceFeatureByDescriptorUseCase useCase;

    @Test
    @DisplayName("등록 결과를 FEATURE_REGISTERED_DESCRIPTOR 로 알린다 — 이미지 등록과 data 구조가 달라 이름을 나눴다")
    void 등록_웹훅() {
        Project project = Project.builder().id(3L).accountId(10L).projectName("p").branchName("b").build();
        BiometricFeature feature = BiometricFeature.builder()
                .id(7L).project(project).type(FeatureType.FACE).featureId("fid")
                .transactionUuid("tx-1").externalKey("emp-1").isDeleted(false).build();
        var input = new CreateFaceFeatureByDescriptorInput(10L, "key", "descriptor", "tx-1", "emp-1");
        given(faceFeatureService.createFaceFeatureByDescriptor(10L, "key", "descriptor", "tx-1", "emp-1"))
                .willReturn(feature);

        FaceFeatureByDescriptorResult result = useCase.execute(input);

        assertThat(result.faceFeatureId()).isEqualTo(7L);
        verify(useCaseNotifyService).notifyWebhook(
                CallerType.API, WebhookEvent.FEATURE_REGISTERED_DESCRIPTOR, 3L, "tx-1", result);
    }
}
