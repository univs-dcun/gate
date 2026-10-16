package ai.univs.gate.modules.feature.application.usecase.face;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.UpdateFeatureDescriptionInput;
import ai.univs.gate.modules.feature.application.usecase.palm.UpdatePalmFeatureDescriptionUseCase;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.file.TestImageUrls;
import ai.univs.gate.support.project.ProjectSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * UG-376: 특징점 메모만 바꾸기 (얼굴·팜). 소유 검증은 단건 조회와 같다 — 남의 특징점이면 바꾸지 않고 INVALID_USER.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UG-376: 특징점 메모 수정")
class UpdateFeatureDescriptionUseCaseTest {

    private static final Long ACCOUNT_ID = 10L;
    private static final String API_KEY = "gate_ug376-api-key";
    private static final Long FEATURE_ID = 7L;

    @Mock private BiometricFeatureRepository biometricFeatureRepository;
    @Mock private ApiKeyService apiKeyService;
    @Mock private FileService fileService;
    @Mock private ProjectSettingsService projectSettingsService;

    private UpdateFaceFeatureDescriptionUseCase face;
    private UpdatePalmFeatureDescriptionUseCase palm;
    private Project mine;
    private Project others;

    @BeforeEach
    void setUp() {
        face = new UpdateFaceFeatureDescriptionUseCase(biometricFeatureRepository, apiKeyService, fileService, projectSettingsService);
        palm = new UpdatePalmFeatureDescriptionUseCase(biometricFeatureRepository, apiKeyService, fileService, projectSettingsService);
        mine = Project.builder().id(1L).accountId(ACCOUNT_ID).branchName("mine").build();
        others = Project.builder().id(2L).accountId(99L).branchName("others").build();
        given(apiKeyService.findOwnedByApiKey(API_KEY, ACCOUNT_ID))
                .willReturn(ApiKey.builder().id(5L).project(mine).apiKey(API_KEY).isActive(true).build());
        given(projectSettingsService.findByProject(mine))
                .willReturn(ProjectSettings.builder().id(3L).project(mine).consentEnabled(true).build());
        given(fileService.imageUrls(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyBoolean())).willAnswer(TestImageUrls.prefix("https://gate.test/api/v1/file?filePath="));
    }

    private BiometricFeature 특징점(FeatureType type, Project project, String description) {
        BiometricFeature feature = BiometricFeature.builder()
                .id(FEATURE_ID).project(project).type(type).featureId("fid").featureImagePath("feature/a.jpg")
                .description(description).isDeleted(false).build();
        given(biometricFeatureRepository.findForUpdateByIdAndTypeAndIsDeletedFalse(FEATURE_ID, type)).willReturn(Optional.of(feature));
        return feature;
    }

    private static UpdateFeatureDescriptionInput 입력(String description) {
        return new UpdateFeatureDescriptionInput(ACCOUNT_ID, API_KEY, FEATURE_ID, description);
    }

    @Test
    @DisplayName("얼굴: 내 특징점이면 메모를 바꾸고, 단건 조회와 같은 모양으로 돌려준다 — 잠그고 읽는다(삭제와 줄 세우기)")
    void 얼굴_메모_변경() {
        BiometricFeature feature = 특징점(FeatureType.FACE, mine, "old");

        var result = face.execute(입력("new memo"));

        assertThat(feature.getDescription()).isEqualTo("new memo");
        assertThat(result.faceFeatureId()).isEqualTo(FEATURE_ID);
        assertThat(result.description()).isEqualTo("new memo");
    }

    @Test
    @DisplayName("팜: 팜 특징점만 찾아 바꾼다")
    void 팜_메모_변경() {
        BiometricFeature feature = 특징점(FeatureType.PALM, mine, "old");

        var result = palm.execute(입력("palm memo"));

        assertThat(feature.getDescription()).isEqualTo("palm memo");
        assertThat(result.palmFeatureId()).isEqualTo(FEATURE_ID);
        verify(biometricFeatureRepository, never()).findForUpdateByIdAndTypeAndIsDeletedFalse(FEATURE_ID, FeatureType.FACE);
    }

    @ParameterizedTest(name = "[{0}]")
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("비우면 메모 없음(null) — 콘솔의 「메모 비우기」")
    void 비우면_null(String blank) {
        BiometricFeature feature = 특징점(FeatureType.FACE, mine, "old");

        face.execute(입력(blank));

        assertThat(feature.getDescription()).isNull();
    }

    @Test
    @DisplayName("남의 프로젝트 특징점이면 바꾸지 않고 INVALID_USER — 설정도 읽지 않는다")
    void 남의_특징점() {
        BiometricFeature feature = 특징점(FeatureType.FACE, others, "victim memo");

        assertThatThrownBy(() -> face.execute(입력("hacked")))
                .isInstanceOf(CustomGateException.class)
                .extracting(e -> ((CustomGateException) e).getErrorType())
                .isEqualTo(ErrorType.INVALID_USER);
        assertThat(feature.getDescription()).isEqualTo("victim memo");
        verify(projectSettingsService, never()).findByProject(mine);
    }

    @Test
    @DisplayName("팜: 남의 프로젝트 특징점이면 바꾸지 않고 INVALID_USER")
    void 팜_남의_특징점() {
        BiometricFeature feature = 특징점(FeatureType.PALM, others, "victim memo");

        assertThatThrownBy(() -> palm.execute(입력("hacked")))
                .isInstanceOf(CustomGateException.class)
                .extracting(e -> ((CustomGateException) e).getErrorType())
                .isEqualTo(ErrorType.INVALID_USER);
        assertThat(feature.getDescription()).isEqualTo("victim memo");
    }

    @Test
    @DisplayName("없거나 지워진(또는 다른 종류의) 특징점이면 INVALID_USER")
    void 없는_특징점() {
        given(biometricFeatureRepository.findForUpdateByIdAndTypeAndIsDeletedFalse(FEATURE_ID, FeatureType.FACE)).willReturn(Optional.empty());

        assertThatThrownBy(() -> face.execute(입력("x")))
                .isInstanceOf(CustomGateException.class)
                .extracting(e -> ((CustomGateException) e).getErrorType())
                .isEqualTo(ErrorType.INVALID_USER);
    }
}
