package ai.univs.gate.facade.demo.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ai.univs.gate.facade.demo.application.input.CreateFaceFeatureByApiKeyInput;
import ai.univs.gate.facade.demo.application.input.CreatePalmFeatureByApiKeyInput;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.support.feature.face.CreateFaceFeatureServiceResult;
import ai.univs.gate.support.feature.face.FaceFeatureService;
import ai.univs.gate.support.feature.palm.CreatePalmFeatureServiceResult;
import ai.univs.gate.support.feature.palm.PalmFeatureService;
import ai.univs.gate.support.file.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 데모 등록이 서비스 결과를 <b>그대로</b> 응답에 옮기는가 (UG-336).
 *
 * <p>이 두 유스케이스에는 테스트가 하나도 없었다. UG-336 이 여기서 API 키와 설정을 먼저 읽던 두
 * 조회를 걷어내고 동의 값을 서비스 결과에서 받게 바꿨다 — 업로드 여부(서비스가 읽은 값)와
 * 응답의 이미지 노출(여기서 따로 읽은 값)이 서로 다른 조회에서 나오던 것을 하나로 합친 것이다.
 *
 * <p>결과 레코드의 두 칸이 모두 {@code boolean} 이라 뒤바뀌어도 컴파일러는 모른다. 그래서 두
 * 값을 <b>일부러 다르게</b> 준다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-336: 데모 등록 유스케이스")
class CreateFeatureByApiKeyUseCaseTest {

    private static final String API_KEY = "gate_demo_key";
    private static final String TX = "tx-demo";
    private static final String FILE_SERVER_PATH = "http://gateway/api/v1/files?filePath=";

    private BiometricFeature feature(FeatureType type) {
        Project project = Project.builder().id(1L).accountId(9L).projectName("demo").branchName("b").build();
        return BiometricFeature.builder()
                .id(7L).project(project).type(type).featureId("fid")
                .featureImagePath("feature/demo.jpg").description("d").isDeleted(false)
                .transactionUuid(TX).build();
    }

    private final MockMultipartFile image =
            new MockMultipartFile("featureImage", "a.jpg", "image/jpeg", new byte[]{1});

    @Nested
    @DisplayName("얼굴")
    class 얼굴 {

        @Mock private FaceFeatureService faceFeatureService;
        @Mock private FileService fileService;
        @InjectMocks private CreateFaceFeatureByApiKeyUseCase useCase;

        @BeforeEach
        void setUp() {
            given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);
        }

        @ParameterizedTest(name = "라이브니스={0}, 동의={1}")
        @CsvSource({"true, false", "false, true"})
        @DisplayName("데모 호출자로 위임하고, checkLiveness 와 이미지 노출이 각자의 값을 따른다")
        void 결과를_그대로_옮긴다(boolean 라이브니스, boolean 동의) {
            given(faceFeatureService.createFaceFeature(CallerType.DEMO, 0L, API_KEY, image, "d", TX, null))
                    .willReturn(new CreateFaceFeatureServiceResult(feature(FeatureType.FACE), 라이브니스, 동의));

            var result = useCase.execute(new CreateFaceFeatureByApiKeyInput(0L, API_KEY, image, "d", TX));

            assertThat(result.checkLiveness()).isEqualTo(라이브니스);
            if (동의) {
                assertThat(result.featureImagePath()).isEqualTo(FILE_SERVER_PATH + "feature/demo.jpg");
            } else {
                assertThat(result.featureImagePath()).as("동의가 꺼지면 이미지 경로를 내보내지 않는다").isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("손바닥")
    class 손바닥 {

        @Mock private PalmFeatureService palmFeatureService;
        @Mock private FileService fileService;
        @InjectMocks private CreatePalmFeatureByApiKeyUseCase useCase;

        @BeforeEach
        void setUp() {
            given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);
        }

        @ParameterizedTest(name = "라이브니스={0}, 동의={1}")
        @CsvSource({"true, false", "false, true"})
        @DisplayName("데모 호출자로 위임하고, checkLiveness 와 이미지 노출이 각자의 값을 따른다")
        void 결과를_그대로_옮긴다(boolean 라이브니스, boolean 동의) {
            given(palmFeatureService.createPalmFeature(CallerType.DEMO, 0L, API_KEY, image, "d", TX, null))
                    .willReturn(new CreatePalmFeatureServiceResult(feature(FeatureType.PALM), 라이브니스, 동의));

            var result = useCase.execute(new CreatePalmFeatureByApiKeyInput(0L, API_KEY, image, "d", TX));

            assertThat(result.checkLiveness()).isEqualTo(라이브니스);
            if (동의) {
                assertThat(result.featureImagePath()).isEqualTo(FILE_SERVER_PATH + "feature/demo.jpg");
            } else {
                assertThat(result.featureImagePath()).as("동의가 꺼지면 이미지 경로를 내보내지 않는다").isEmpty();
            }
        }
    }
}
