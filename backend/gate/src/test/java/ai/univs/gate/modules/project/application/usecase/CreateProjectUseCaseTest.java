package ai.univs.gate.modules.project.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.domain.repository.ApiKeyRepository;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.application.input.CreateProjectInput;
import ai.univs.gate.modules.project.application.result.ProjectResult;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectLivenessSetting;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.LivenessOperation;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.modules.project.domain.repository.ProjectLivenessSettingRepository;
import ai.univs.gate.modules.project.domain.repository.ProjectRepository;
import ai.univs.gate.modules.project.domain.repository.ProjectSettingsRepository;
import ai.univs.gate.support.api_key.ApiKeyGenerator;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateProjectUseCase 단위 테스트")
class CreateProjectUseCaseTest {

    private static final Long ACCOUNT_ID = 10L;
    private static final Long SAVED_PROJECT_ID = 1L;
    private static final Long SAVED_API_KEY_ID = 5L;
    private static final Long SAVED_SETTINGS_ID = 2L;
    private static final int API_KEY_EXPIRY_DAYS = 365;
    private static final String GENERATED_API_KEY = "gate_ABCDEFGHIJKLMNOPQRSTUVWXYZ12345";
    private static final String GENERATED_SECRET_KEY = "generated-secret-key";

    @Mock private ProjectRepository projectRepository;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private ProjectSettingsRepository projectSettingsRepository;
    @Mock private ProjectLivenessSettingRepository livenessSettingRepository;
    @Mock private ApiKeyGenerator apiKeyGenerator;

    @InjectMocks private CreateProjectUseCase createProjectUseCase;

    private CreateProjectInput input;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(createProjectUseCase, "apiKeyExpiryDays", API_KEY_EXPIRY_DAYS);

        input = new CreateProjectInput(ACCOUNT_ID, "gate-project", "e-KYC 프로젝트", "#FF0000");

        given(projectRepository.save(any(Project.class))).willAnswer(invocation -> {
            Project saved = invocation.getArgument(0);
            saved.setId(SAVED_PROJECT_ID);
            return saved;
        });
        given(apiKeyGenerator.generateApiKey()).willReturn(GENERATED_API_KEY);
        given(apiKeyGenerator.generateSecretKey()).willReturn(GENERATED_SECRET_KEY);
        given(apiKeyRepository.save(any(ApiKey.class))).willAnswer(invocation -> {
            ApiKey saved = invocation.getArgument(0);
            saved.setId(SAVED_API_KEY_ID);
            return saved;
        });
        given(projectSettingsRepository.save(any(ProjectSettings.class))).willAnswer(invocation -> {
            ProjectSettings saved = invocation.getArgument(0);
            saved.setId(SAVED_SETTINGS_ID);
            return saved;
        });
    }

    @Test
    @DisplayName("프로젝트가 입력 값과 ACTIVE 상태, 랜덤 UUID branchName으로 저장되고 결과에 API 키가 노출된다")
    void execute_savesProjectAndReturnsResult() {
        // when
        ProjectResult result = createProjectUseCase.execute(input);

        // then: 저장된 프로젝트 필드 검증
        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(projectCaptor.capture());
        Project savedProject = projectCaptor.getValue();
        assertThat(savedProject.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(savedProject.getProjectName()).isEqualTo("gate-project");
        assertThat(savedProject.getProjectDescription()).isEqualTo("e-KYC 프로젝트");
        assertThat(savedProject.getColorTag()).isEqualTo("#FF0000");
        assertThat(savedProject.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(savedProject.isDeleted()).isFalse();
        assertThatCode(() -> UUID.fromString(savedProject.getBranchName())).doesNotThrowAnyException();

        // then: 결과 필드 검증 (생성 직후에는 전체 API 키가 노출되어야 한다)
        assertThat(result.projectId()).isEqualTo(SAVED_PROJECT_ID);
        assertThat(result.projectName()).isEqualTo("gate-project");
        assertThat(result.projectDescription()).isEqualTo("e-KYC 프로젝트");
        assertThat(result.colorTag()).isEqualTo("#FF0000");
        assertThat(result.status()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(result.apiKey()).isEqualTo(GENERATED_API_KEY);
    }

    @Test
    @DisplayName("API 키가 프로젝트에 연결되어 활성 상태와 만료일과 함께 자동 발급된다")
    void execute_issuesApiKey() {
        // when
        LocalDateTime beforeUtc = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime beforeLocal = LocalDateTime.now();
        createProjectUseCase.execute(input);
        LocalDateTime afterUtc = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime afterLocal = LocalDateTime.now();

        // then: 저장된 API 키 필드 검증
        ArgumentCaptor<ApiKey> apiKeyCaptor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(apiKeyCaptor.capture());
        ApiKey savedApiKey = apiKeyCaptor.getValue();
        assertThat(savedApiKey.getProject().getId()).isEqualTo(SAVED_PROJECT_ID);
        assertThat(savedApiKey.getApiKey()).isEqualTo(GENERATED_API_KEY);
        assertThat(savedApiKey.getSecretKey()).isEqualTo(GENERATED_SECRET_KEY);
        assertThat(savedApiKey.getIsActive()).isTrue();
        // issuedAt은 UTC 기준
        assertThat(savedApiKey.getIssuedAt()).isBetween(beforeUtc, afterUtc);
        // expiresAt은 시스템 기본 타임존 now() + 만료일 기준 (프로덕션 코드 동작 그대로 검증)
        assertThat(savedApiKey.getExpiresAt()).isBetween(
                beforeLocal.plusDays(API_KEY_EXPIRY_DAYS), afterLocal.plusDays(API_KEY_EXPIRY_DAYS));
    }

    @Test
    @DisplayName("설정이 consent 비활성 기본값으로 저장되고 FACE/PALM 각각 REGISTER만 꺼진 라이브니스 기본값 8건이 저장된다")
    void execute_initializesSettingsWithLivenessDefaults() {
        // when
        createProjectUseCase.execute(input);

        // then: 프로젝트 설정 기본값 검증
        ArgumentCaptor<ProjectSettings> settingsCaptor = ArgumentCaptor.forClass(ProjectSettings.class);
        verify(projectSettingsRepository).save(settingsCaptor.capture());
        ProjectSettings savedSettings = settingsCaptor.getValue();
        assertThat(savedSettings.getProject().getId()).isEqualTo(SAVED_PROJECT_ID);
        assertThat(savedSettings.getConsentEnabled()).isFalse();

        // then: 라이브니스 기본값 검증 — FACE, PALM 순서로 saveAll 2회
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProjectLivenessSetting>> livenessCaptor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(livenessSettingRepository, times(2)).saveAll(livenessCaptor.capture());
        List<List<ProjectLivenessSetting>> allBatches = livenessCaptor.getAllValues();
        assertThat(allBatches).hasSize(2);

        assertLivenessDefaults(allBatches.get(0), FeatureType.FACE, savedSettings);
        assertLivenessDefaults(allBatches.get(1), FeatureType.PALM, savedSettings);
    }

    private void assertLivenessDefaults(List<ProjectLivenessSetting> batch,
                                        FeatureType expectedType,
                                        ProjectSettings expectedSettings) {
        assertThat(batch).hasSize(4);
        assertThat(batch).allSatisfy(setting -> {
            assertThat(setting.getModuleType()).isEqualTo(expectedType);
            assertThat(setting.getProjectSettings()).isSameAs(expectedSettings);
        });
        assertThat(batch)
                .extracting(ProjectLivenessSetting::getOperation, ProjectLivenessSetting::getEnabled)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LivenessOperation.REGISTER, false),
                        org.assertj.core.groups.Tuple.tuple(LivenessOperation.IDENTIFY, true),
                        org.assertj.core.groups.Tuple.tuple(LivenessOperation.VERIFY_ID, true),
                        org.assertj.core.groups.Tuple.tuple(LivenessOperation.VERIFY_IMAGE, true));
    }
}
