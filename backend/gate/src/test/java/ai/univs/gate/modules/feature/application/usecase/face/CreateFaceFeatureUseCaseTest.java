package ai.univs.gate.modules.feature.application.usecase.face;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.CreateFeatureInput;
import ai.univs.gate.modules.feature.application.result.face.FaceFeatureResult;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.support.feature.face.CreateFaceFeatureServiceResult;
import ai.univs.gate.support.feature.face.FaceFeatureService;
import ai.univs.gate.support.file.FileService;
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
@DisplayName("CreateFaceFeatureUseCase 단위 테스트")
class CreateFaceFeatureUseCaseTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final String API_KEY = "gate_test-api-key";
    private static final String TRANSACTION_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String FILE_SERVER_PATH = "http://gateway/api/v1/files?filePath=";

    @Mock private FaceFeatureService faceFeatureService;
    @Mock private FileService fileService;

    @InjectMocks private CreateFaceFeatureUseCase createFaceFeatureUseCase;

    private Project project;
    private ApiKey apiKey;
    private MockMultipartFile featureImage;
    private CreateFeatureInput input;
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
                "featureImage", "face.jpg", "image/jpeg", "face-bytes".getBytes());
        input = new CreateFeatureInput(ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID, "ext-face-1");
        feature = BiometricFeature.builder()
                .id(7L)
                .project(project)
                .type(FeatureType.FACE)
                .featureId("new-face-id")
                .featureImagePath("feature/registered.jpg")
                .description("홍길동")
                .isDeleted(false)
                .transactionUuid(TRANSACTION_UUID)
                .build();
    }


    @Test
    @DisplayName("입력 값이 그대로 서비스에 위임되고 서비스 결과가 FaceFeatureResult로 매핑된다")
    void execute_delegatesAndMapsResult() {
        // given: 입력 값과 정확히 일치하는 인자로만 스텁하여 위임 인자를 검증한다
        given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);
        given(faceFeatureService.createFaceFeature(
                        CallerType.API, ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID, "ext-face-1"))
                .willReturn(new CreateFaceFeatureServiceResult(feature, true, true));

        // when
        FaceFeatureResult result = createFaceFeatureUseCase.execute(input);

        // then
        assertThat(result.faceFeatureId()).isEqualTo(7L);
        assertThat(result.projectId()).isEqualTo(PROJECT_ID);
        assertThat(result.featureId()).isEqualTo("new-face-id");
        assertThat(result.description()).isEqualTo("홍길동");
        assertThat(result.featureImagePath()).isEqualTo(FILE_SERVER_PATH + "feature/registered.jpg");
        assertThat(result.transactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(result.checkLiveness()).isTrue();
    }

    @Test
    @DisplayName("동의(consent)가 비활성화면 이미지 경로가 비어 있고 livenessChecked=false가 그대로 매핑된다")
    void execute_consentDisabled_hidesImagePath() {
        // given
        given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);
        given(faceFeatureService.createFaceFeature(
                        CallerType.API, ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID, "ext-face-1"))
                .willReturn(new CreateFaceFeatureServiceResult(feature, false, false));

        // when
        FaceFeatureResult result = createFaceFeatureUseCase.execute(input);

        // then
        assertThat(result.featureImagePath()).isEmpty();
        assertThat(result.checkLiveness()).isFalse();
        assertThat(result.featureId()).isEqualTo("new-face-id");
    }

    /**
     * <b>동의와 라이브니스가 서로 다를 때 각 값이 제자리로 간다</b> (UG-336 델타 리뷰).
     *
     * <p>결과 레코드의 두 칸이 모두 {@code boolean} 이라 뒤바뀌어도 컴파일러는 모른다. 나머지
     * 테스트는 두 값을 늘 같게 두어 뒤바뀌어도 초록이었다. 뒤바뀌면 동의가 꺼졌는데 응답에
     * 이미지 경로가 나가거나 {@code checkLiveness} 가 틀린다.
     */
    @org.junit.jupiter.params.ParameterizedTest(name = "라이브니스={0}, 동의={1}")
    @org.junit.jupiter.params.provider.CsvSource({"true, false", "false, true"})
    @DisplayName("UG-336: 동의와 라이브니스가 다르면 checkLiveness 와 이미지 노출이 각자의 값을 따른다")
    void 동의와_라이브니스가_다르면_제자리로_간다(boolean 라이브니스, boolean 동의) {
        given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);
        given(faceFeatureService.createFaceFeature(
                        CallerType.API, ACCOUNT_ID, API_KEY, featureImage, "홍길동", TRANSACTION_UUID, "ext-face-1"))
                .willReturn(new CreateFaceFeatureServiceResult(feature, 라이브니스, 동의));

        FaceFeatureResult result = createFaceFeatureUseCase.execute(input);

        assertThat(result.checkLiveness()).isEqualTo(라이브니스);
        if (동의) {
            assertThat(result.featureImagePath()).isNotEmpty();
        } else {
            assertThat(result.featureImagePath()).as("동의가 꺼지면 이미지 경로를 내보내지 않는다").isEmpty();
        }
    }
}
