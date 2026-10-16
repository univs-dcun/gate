package ai.univs.gate.modules.feature.application.usecase.face;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.face.VerifyByImageInput;
import ai.univs.gate.modules.feature.application.result.face.VerifyByImageResult;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.VerifyFaceByImageFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.face.FaceService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.file.TestImageUrls;
import ai.univs.gate.support.history.HistoryRecorder;
import ai.univs.gate.support.notify.UseCaseNotifyService;
import ai.univs.gate.support.project.ProjectSettingsService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

/** 1:1 사진 인증의 하위 오류 흡수 (UG-391) — 촬영 인증 · 1:N 과 같은 규칙인지 지킨다. */
@ExtendWith(MockitoExtension.class)
@DisplayName("FaceVerifyByFeatureImageUseCase — 하위 오류 흡수 (UG-391)")
class FaceVerifyByFeatureImageUseCaseTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final String API_KEY = "gate_test-api-key";
    private static final String TRANSACTION_UUID = "550e8400-e29b-41d4-a716-446655440009";
    private static final String FILE_SERVER_PATH = "http://gateway/api/v1/files?filePath=";

    @Mock private HistoryRecorder historyRecorder;
    @Mock private FileService fileService;
    @Mock private FaceService faceService;
    @Mock private ApiKeyService apiKeyService;
    @Mock private ProjectSettingsService projectSettingsService;
    @Mock private UseCaseNotifyService useCaseNotifyService;

    @InjectMocks private FaceVerifyByFeatureImageUseCase useCase;

    private VerifyByImageInput input;

    @BeforeEach
    void setUp() {
        Project project = Project.builder()
                .id(PROJECT_ID).accountId(ACCOUNT_ID).projectName("gate-project").branchName("branch-1")
                .status(ProjectStatus.ACTIVE).build();
        ApiKey apiKey = ApiKey.builder()
                .id(5L).project(project).apiKey(API_KEY).secretKey("secret")
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC)).isActive(true).build();
        MockMultipartFile document = new MockMultipartFile("documentImage", "id.jpg", "image/jpeg", "doc".getBytes());
        MockMultipartFile face = new MockMultipartFile("matchingFeatureImage", "face.jpg", "image/jpeg", "face".getBytes());
        input = new VerifyByImageInput(CallerType.API, ACCOUNT_ID, API_KEY, document, face, TRANSACTION_UUID);

        ProjectSettings settings = ProjectSettings.builder().id(2L).project(project).consentEnabled(true).build();
        given(apiKeyService.findByApiKey(CallerType.API, API_KEY, ACCOUNT_ID)).willReturn(apiKey);
        given(projectSettingsService.findByProject(project)).willReturn(settings);
        given(projectSettingsService.isLivenessEnabled(settings, FeatureType.FACE, LivenessOperation.VERIFY_IMAGE))
                .willReturn(true);
        given(historyRecorder.start(any(MatchHistory.class))).willAnswer(invocation -> {
            MatchHistory saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 300L);
            return saved;
        });
    }

    private MatchHistory capturedMatchHistory() {
        ArgumentCaptor<MatchHistory> captor = ArgumentCaptor.forClass(MatchHistory.class);
        verify(historyRecorder).fail(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("엔진 판정 거절(-777)은 사유가 목록에 없어도(LIVENESS_NOT_PASSED) 200 실패로 흡수한다")
    void engineRejectUnlistedReason_absorbed() {
        given(faceService.verifyByImage(any(VerifyFaceByImageFeignRequestDTO.class)))
                .willThrow(new CustomFeignException("-777", "LIVENESS_NOT_PASSED", "liveness failed"));
        given(fileService.imageUrls(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyBoolean()))
                .willAnswer(TestImageUrls.prefix(FILE_SERVER_PATH));

        VerifyByImageResult result = useCase.execute(input);

        assertThat(result.success()).isFalse();
        assertThat(result.failureType()).isEqualTo("LIVENESS_NOT_PASSED");
        assertThat(capturedMatchHistory().getFailureType()).isEqualTo("LIVENESS_NOT_PASSED");
    }

    @Test
    @DisplayName("엔진 거절이 아니고 목록에도 없는 하위 오류는 사유를 남긴 뒤 그대로 전파된다")
    void otherFeignError_rethrown() {
        CustomFeignException e = new CustomFeignException("ML-500", "INTERNAL_ERROR", "boom");
        given(faceService.verifyByImage(any(VerifyFaceByImageFeignRequestDTO.class))).willThrow(e);

        assertThatThrownBy(() -> useCase.execute(input)).isSameAs(e);
        assertThat(capturedMatchHistory().getFailureType()).isEqualTo("INTERNAL_ERROR");
        verifyNoInteractions(useCaseNotifyService);
    }
}
