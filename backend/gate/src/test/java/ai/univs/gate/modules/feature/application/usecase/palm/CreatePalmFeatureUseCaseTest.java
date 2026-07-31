package ai.univs.gate.modules.feature.application.usecase.palm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.palm.CreatePalmFeatureInput;
import ai.univs.gate.modules.feature.application.result.palm.PalmFeatureResult;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.palm.CreatePalmFeatureServiceResult;
import ai.univs.gate.support.feature.palm.PalmFeatureService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreatePalmFeatureUseCase 단위 테스트")
class CreatePalmFeatureUseCaseTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final String API_KEY = "gate_test-api-key";
    private static final String TRANSACTION_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String FILE_SERVER_PATH = "http://gateway/api/v1/files?filePath=";

    @Mock private PalmFeatureService palmFeatureService;
    @Mock private ApiKeyService apiKeyService;
    @Mock private FileService fileService;
    @Mock private ProjectSettingsService projectSettingsService;

    @InjectMocks private CreatePalmFeatureUseCase createPalmFeatureUseCase;

    private Project project;
    private ApiKey apiKey;
    private MockMultipartFile featureImage;
    private CreatePalmFeatureInput input;
    private BiometricFeature feature;

    @BeforeEach
    void setUp() {
        project = Project.builder()
                .id(PROJECT_ID)
                .accountId(ACCOUNT_ID)
                .projectName("gate-project")
                .branchName("branch-1")
                .status(ProjectStatus.ACTIVE)
                .build();
        apiKey = ApiKey.builder()
                .id(5L)
                .project(project)
                .apiKey(API_KEY)
                .secretKey("secret")
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC))
                .isActive(true)
                .build();
        featureImage = new MockMultipartFile(
                "featureImage", "palm.jpg", "image/jpeg", "palm-bytes".getBytes());
        input = new CreatePalmFeatureInput(
                ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID, "external-key-1");
        feature = BiometricFeature.builder()
                .id(7L)
                .project(project)
                .type(FeatureType.PALM)
                .featureId("new-palm-id")
                .featureImagePath("feature/registered-palm.jpg")
                .description("홍길동")
                .isDeleted(false)
                .transactionUuid(TRANSACTION_UUID)
                .build();
    }

    private void givenProjectSettings(boolean consentEnabled) {
        ProjectSettings settings = ProjectSettings.builder()
                .id(2L)
                .project(project)
                .consentEnabled(consentEnabled)
                .build();
        given(apiKeyService.findByApiKey(API_KEY)).willReturn(apiKey);
        given(projectSettingsService.findByProject(project)).willReturn(settings);
        given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);
    }

    @Test
    @DisplayName("입력 값이 그대로 서비스에 위임되고 서비스 결과가 PalmFeatureResult로 매핑된다")
    void execute_delegatesAndMapsResult() {
        // given: 입력 값과 정확히 일치하는 인자로만 스텁하여 위임 인자를 검증한다
        // (input의 externalKey는 서비스로 전달되지 않는다 — 프로덕션 코드 현재 동작)
        givenProjectSettings(true);
        given(palmFeatureService.createPalmFeature(
                        ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID))
                .willReturn(new CreatePalmFeatureServiceResult(feature, true));

        // when
        PalmFeatureResult result = createPalmFeatureUseCase.execute(input);

        // then
        assertThat(result.palmFeatureId()).isEqualTo(7L);
        assertThat(result.projectId()).isEqualTo(PROJECT_ID);
        assertThat(result.featureId()).isEqualTo("new-palm-id");
        assertThat(result.description()).isEqualTo("홍길동");
        assertThat(result.featureImagePath()).isEqualTo(FILE_SERVER_PATH + "feature/registered-palm.jpg");
        assertThat(result.transactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(result.checkLiveness()).isTrue();
    }

    @Test
    @DisplayName("동의(consent)가 비활성화면 이미지 경로가 비어 있고 livenessChecked=false가 그대로 매핑된다")
    void execute_consentDisabled_hidesImagePath() {
        // given
        givenProjectSettings(false);
        given(palmFeatureService.createPalmFeature(
                        ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID))
                .willReturn(new CreatePalmFeatureServiceResult(feature, false));

        // when
        PalmFeatureResult result = createPalmFeatureUseCase.execute(input);

        // then
        assertThat(result.featureImagePath()).isEmpty();
        assertThat(result.checkLiveness()).isFalse();
        assertThat(result.featureId()).isEqualTo("new-palm-id");
    }
}
