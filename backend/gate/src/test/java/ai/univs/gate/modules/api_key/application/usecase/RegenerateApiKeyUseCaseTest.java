package ai.univs.gate.modules.api_key.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ai.univs.gate.modules.api_key.application.result.ApiKeyResult;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.domain.repository.ApiKeyRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyGenerator;
import ai.univs.gate.support.project.ProjectService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
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
@DisplayName("RegenerateApiKeyUseCase 단위 테스트")
class RegenerateApiKeyUseCaseTest {

    private static final Long ACCOUNT_ID = 10L;
    private static final Long PROJECT_ID = 1L;
    private static final int API_KEY_EXPIRY_DAYS = 365;
    // 36자 (prefix 5자 + 31자) — maskApiKey의 20자 이상 마스킹 조건을 만족
    private static final String NEW_API_KEY = "gate_ABCDEFGHIJKLMNOPQRSTUVWXYZ12345";
    private static final String NEW_SECRET_KEY = "new-secret-key";

    @Mock private ProjectService projectService;
    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private ApiKeyGenerator apiKeyGenerator;

    @InjectMocks private RegenerateApiKeyUseCase regenerateApiKeyUseCase;

    private Project project;
    private ApiKey oldApiKey;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(regenerateApiKeyUseCase, "apiKeyExpiryDays", API_KEY_EXPIRY_DAYS);

        project = Project.builder()
                .id(PROJECT_ID)
                .accountId(ACCOUNT_ID)
                .projectName("gate-project")
                .branchName("branch-1")
                .status(ProjectStatus.ACTIVE)
                .build();
        oldApiKey = ApiKey.builder()
                .id(5L)
                .project(project)
                .apiKey("gate_OLDKEYOLDKEYOLDKEYOLDKEYOLDKEY")
                .secretKey("old-secret")
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(30))
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(335))
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("재발급 성공 시 기존 키는 비활성화되고 새 키가 활성 상태로 저장된다")
    void execute_success() {
        // given
        given(apiKeyRepository.findActiveByProjectId(PROJECT_ID)).willReturn(Optional.of(oldApiKey));
        given(apiKeyGenerator.generateApiKey()).willReturn(NEW_API_KEY);
        given(apiKeyGenerator.generateSecretKey()).willReturn(NEW_SECRET_KEY);
        given(apiKeyRepository.save(any(ApiKey.class))).willAnswer(invocation -> {
            ApiKey saved = invocation.getArgument(0);
            saved.setId(6L);
            return saved;
        });

        // when
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        ApiKeyResult result = regenerateApiKeyUseCase.execute(ACCOUNT_ID, PROJECT_ID);
        LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);

        // then: 기존 키 상태 전이 검증
        assertThat(oldApiKey.getIsActive()).isFalse();

        // then: 저장된 새 키 검증
        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKey newApiKey = captor.getValue();
        assertThat(newApiKey.getProject()).isSameAs(project);
        assertThat(newApiKey.getApiKey()).isEqualTo(NEW_API_KEY);
        assertThat(newApiKey.getSecretKey()).isEqualTo(NEW_SECRET_KEY);
        assertThat(newApiKey.getIsActive()).isTrue();
        assertThat(newApiKey.getIssuedAt()).isBetween(before, after);
        assertThat(newApiKey.getExpiresAt())
                .isEqualTo(newApiKey.getIssuedAt().plusDays(API_KEY_EXPIRY_DAYS));

        // then: 결과 필드 검증 (재발급 시 전체 키가 노출되어야 한다)
        assertThat(result.apiKeyId()).isEqualTo(6L);
        assertThat(result.apiKey()).isEqualTo(NEW_API_KEY);
        assertThat(result.maskedApiKey()).isEqualTo("gate_ABCDEFG****2345");
        assertThat(result.issuedAt()).isEqualTo(newApiKey.getIssuedAt());
        assertThat(result.expiresAt()).isEqualTo(newApiKey.getExpiresAt());
        assertThat(result.isActive()).isTrue();

        // then: 소유권 검증 호출 (projectId, accountId 순서)
        verify(projectService).validateOwnership(PROJECT_ID, ACCOUNT_ID);
    }

    @Test
    @DisplayName("활성 키가 없으면 API_KEY_NOT_FOUND 예외가 발생하고 저장은 일어나지 않는다")
    void execute_activeKeyNotFound_throwsException() {
        // given
        given(apiKeyRepository.findActiveByProjectId(PROJECT_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> regenerateApiKeyUseCase.execute(ACCOUNT_ID, PROJECT_ID))
                .isInstanceOf(CustomGateException.class)
                .satisfies(e -> assertThat(((CustomGateException) e).getErrorType())
                        .isEqualTo(ErrorType.API_KEY_NOT_FOUND));

        // then: 새 키 발급/저장이 일어나지 않아야 한다
        verify(apiKeyRepository, never()).save(any(ApiKey.class));
        verifyNoInteractions(apiKeyGenerator);
    }
}
