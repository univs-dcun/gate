package ai.univs.gate.modules.feature.application.usecase.palm;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.palm.GetPalmFeatureInput;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * UG-281 반박 리뷰에서 발견한 결함의 회귀 테스트.
 *
 * <p>이 UseCase 는 API 키의 소유만 확인하고, <b>조회한 특징점이 그 키의 프로젝트 것인지는 보지
 * 않았다.</b> 특징점 ID 가 순번({@code IDENTITY})이라 1부터 훑는 것만으로 남의 팜 특징점을 읽을 수
 * 있었다. Face 단건 조회·Palm 삭제·Palm 수정에는 같은 검사가 있었고 이 경로에만 빠져 있었다.
 *
 * <p>특히 위험했던 이유는 {@code consentEnabled} 를 <b>호출자</b> 프로젝트 설정에서 가져온다는
 * 점이다. 공격자가 자기 프로젝트의 동의만 켜면, 피해자가 동의를 꺼 두었어도 응답에
 * {@code featureImagePath} 가 채워져 나갔다. 그 경로는 {@code /api/v1/file} 이 무인증으로 서빙한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-281: Palm 단건 조회의 프로젝트 스코프 검증")
class GetPalmFeatureUseCaseTest {

    private static final Long CALLER_ACCOUNT_ID = 10L;
    private static final String API_KEY = "gate_caller-api-key";
    private static final Long PALM_FEATURE_ID = 1L;

    @Mock private BiometricFeatureRepository biometricFeatureRepository;
    @Mock private ApiKeyService apiKeyService;
    @Mock private FileService fileService;
    @Mock private ProjectSettingsService projectSettingsService;

    @InjectMocks private GetPalmFeatureUseCase getPalmFeatureUseCase;

    private Project callerProject;
    private Project victimProject;
    private GetPalmFeatureInput input;

    @BeforeEach
    void setUp() {
        callerProject = Project.builder().id(1L).accountId(CALLER_ACCOUNT_ID).branchName("caller").build();
        victimProject = Project.builder().id(2L).accountId(99L).branchName("victim").build();

        ApiKey apiKey = ApiKey.builder().id(5L).project(callerProject).apiKey(API_KEY).isActive(true).build();
        given(apiKeyService.findOwnedByApiKey(API_KEY, CALLER_ACCOUNT_ID)).willReturn(apiKey);

        input = new GetPalmFeatureInput(CALLER_ACCOUNT_ID, API_KEY, PALM_FEATURE_ID);
    }

    private void 특징점이_속한_프로젝트(Project project) {
        BiometricFeature feature = BiometricFeature.builder()
                .id(PALM_FEATURE_ID)
                .project(project)
                .type(FeatureType.PALM)
                .featureId("palm-id")
                .featureImagePath("feature/palm.jpg")
                .isDeleted(false)
                .build();
        given(biometricFeatureRepository.findByIdAndTypeAndIsDeletedFalse(PALM_FEATURE_ID, FeatureType.PALM))
                .willReturn(Optional.of(feature));
    }

    @Test
    @DisplayName("남의 프로젝트 특징점이면 거부한다")
    void 타_프로젝트_특징점_거부() {
        특징점이_속한_프로젝트(victimProject);

        assertThatThrownBy(() -> getPalmFeatureUseCase.execute(input))
                .isInstanceOf(CustomGateException.class)
                .satisfies(e -> assertThat(((CustomGateException) e).getErrorType())
                        .isEqualTo(ErrorType.INVALID_USER));

        // 호출자 설정을 읽기 전에 막아야 한다. 읽고 나서 막으면 '공격자 동의 설정으로 피해자
        // 이미지 경로를 노출' 하는 원래 결함이 형태만 바꿔 남는다.
        verify(projectSettingsService, never()).findByProject(callerProject);
    }

    @Test
    @DisplayName("내 프로젝트 특징점이면 반환한다")
    void 자기_프로젝트_특징점_통과() {
        특징점이_속한_프로젝트(callerProject);
        given(projectSettingsService.findByProject(callerProject))
                .willReturn(ProjectSettings.builder().id(2L).project(callerProject).consentEnabled(true).build());
        given(fileService.getFileServerPath()).willReturn("https://gate.test/api/v1/file?filePath=");

        assertThat(getPalmFeatureUseCase.execute(input).palmFeatureId()).isEqualTo(PALM_FEATURE_ID);
    }
}
