package ai.univs.gate.modules.api_key.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
        given(projectService.validateOwnershipForUpdate(PROJECT_ID, ACCOUNT_ID)).willReturn(project);
        given(apiKeyRepository.findAllActiveByProjectId(PROJECT_ID)).willReturn(List.of(oldApiKey));
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
        // UG-302: 잠그는 쪽을 써야 한다. 잠그지 않으면 동시 재발급이 활성 키를 2개로
        // 만들고, 그 순간 상세 조회와 재발급이 둘 다 막힌다.
        verify(projectService).validateOwnershipForUpdate(PROJECT_ID, ACCOUNT_ID);
    }

    @Test
    @DisplayName("활성 키가 없으면 API_KEY_NOT_FOUND 예외가 발생하고 저장은 일어나지 않는다")
    void execute_activeKeyNotFound_throwsException() {
        // given
        given(apiKeyRepository.findAllActiveByProjectId(PROJECT_ID)).willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> regenerateApiKeyUseCase.execute(ACCOUNT_ID, PROJECT_ID))
                .isInstanceOf(CustomGateException.class)
                .satisfies(e -> assertThat(((CustomGateException) e).getErrorType())
                        .isEqualTo(ErrorType.API_KEY_NOT_FOUND));

        // then: 새 키 발급/저장이 일어나지 않아야 한다
        verify(apiKeyRepository, never()).save(any(ApiKey.class));
        verifyNoInteractions(apiKeyGenerator);
    }

    /**
     * 활성 키가 2개인 프로젝트도 재발급 한 번으로 정상으로 돌아온다 (UG-302).
     *
     * <p>예전에는 이 상황에서 재발급이 {@code IncorrectResultSizeDataAccessException} 으로 500 이
     * 됐다. 상세 조회도 같은 이유로 500 이었으므로, 그 프로젝트는 <b>스스로 빠져나올 방법이
     * 없었다</b> — 고칠 유일한 수단이 고쳐야 할 상태 때문에 막히는 구조였다.
     */
    @Test
    @DisplayName("활성 키가 2개여도 전부 끄고 하나만 새로 발급한다 — 어긋난 상태가 이 호출로 정리된다")
    void 활성_두개도_정리된다() {
        ApiKey 다른활성키 = ApiKey.builder()
                .id(9L)
                .project(project)
                .apiKey("gate_DUPDUPDUPDUPDUPDUPDUPDUPDUPDUPD")
                .secretKey("dup-secret")
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(1))
                .isActive(true)
                .build();

        given(projectService.validateOwnershipForUpdate(PROJECT_ID, ACCOUNT_ID)).willReturn(project);
        given(apiKeyRepository.findAllActiveByProjectId(PROJECT_ID))
                .willReturn(List.of(oldApiKey, 다른활성키));
        given(apiKeyGenerator.generateApiKey()).willReturn(NEW_API_KEY);
        given(apiKeyGenerator.generateSecretKey()).willReturn(NEW_SECRET_KEY);
        given(apiKeyRepository.save(any(ApiKey.class))).willAnswer(i -> i.getArgument(0));

        regenerateApiKeyUseCase.execute(ACCOUNT_ID, PROJECT_ID);

        assertThat(oldApiKey.getIsActive())
                .as("하나만 끄면 활성 2개 상태가 그대로 남아 다음 조회가 또 어긋난다")
                .isFalse();
        assertThat(다른활성키.getIsActive()).isFalse();
        verify(apiKeyRepository, times(1)).save(any(ApiKey.class));
    }

    /**
     * 잠금이 키 읽기보다 <b>먼저</b>여야 한다 (반박 리뷰 지적).
     *
     * <p>{@code verify(projectService).validateOwnershipForUpdate(...)} 는 <b>호출 여부만</b>
     * 본다. 두 줄의 순서만 뒤집어 키를 먼저 읽게 하면 TOCTOU 가 그대로 부활하는데 테스트는
     * 초록이었다 — 리뷰어가 그 변이를 심고 실제 H2 2스레드로 활성 키 2개를 만들었다.
     * 잠금은 "부르기만" 하면 되는 것이 아니라 "먼저" 불러야 한다.
     */
    @Test
    @DisplayName("잠금을 먼저 잡고 그다음 활성 키를 읽는다")
    void 잠금이_읽기보다_먼저다() {
        given(projectService.validateOwnershipForUpdate(PROJECT_ID, ACCOUNT_ID)).willReturn(project);
        given(apiKeyRepository.findAllActiveByProjectId(PROJECT_ID)).willReturn(List.of(oldApiKey));
        given(apiKeyGenerator.generateApiKey()).willReturn(NEW_API_KEY);
        given(apiKeyGenerator.generateSecretKey()).willReturn(NEW_SECRET_KEY);
        given(apiKeyRepository.save(any(ApiKey.class))).willAnswer(i -> i.getArgument(0));

        regenerateApiKeyUseCase.execute(ACCOUNT_ID, PROJECT_ID);

        InOrder inOrder = org.mockito.Mockito.inOrder(projectService, apiKeyRepository);
        inOrder.verify(projectService).validateOwnershipForUpdate(PROJECT_ID, ACCOUNT_ID);
        inOrder.verify(apiKeyRepository).findAllActiveByProjectId(PROJECT_ID);
    }

    /** 활성 키가 2개면 정리했다는 사실을 ERROR 로 남긴다 — 조용히 지나가면 원인을 못 찾는다. */
    @Test
    @DisplayName("중복 정리는 ERROR 로 남는다")
    void 중복_정리는_ERROR_로_남는다() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(RegenerateApiKeyUseCase.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ApiKey 다른활성키 = ApiKey.builder().id(9L).project(project)
                    .apiKey("gate_DUPDUPDUPDUPDUPDUPDUPDUPDUPDUPD").secretKey("dup")
                    .issuedAt(LocalDateTime.now(ZoneOffset.UTC)).isActive(true).build();
            given(projectService.validateOwnershipForUpdate(PROJECT_ID, ACCOUNT_ID))
                    .willReturn(project);
            given(apiKeyRepository.findAllActiveByProjectId(PROJECT_ID))
                    .willReturn(List.of(oldApiKey, 다른활성키));
            given(apiKeyGenerator.generateApiKey()).willReturn(NEW_API_KEY);
            given(apiKeyGenerator.generateSecretKey()).willReturn(NEW_SECRET_KEY);
            given(apiKeyRepository.save(any(ApiKey.class))).willAnswer(i -> i.getArgument(0));

            regenerateApiKeyUseCase.execute(ACCOUNT_ID, PROJECT_ID);

            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR);
                assertThat(event.getFormattedMessage()).contains(String.valueOf(PROJECT_ID));
            });
        } finally {
            logger.detachAppender(appender);
        }
    }

    /**
     * 비활성화를 <b>새 키 삽입 전에</b> DB 로 내보낸다 (반박 리뷰가 찾은 블로커).
     *
     * <p>{@code deactivate()} 는 더티 마킹일 뿐이고 그 사이 {@code ApiKeyGenerator} 는 DB 를
     * 건드리지 않아 auto-flush 가 없다. IDENTITY 라 {@code save()} 가 INSERT 를 즉시
     * 내보내는데, 그때 DB 에는 기존 행이 아직 활성이다 — V24 의 부분 유니크 인덱스가 그
     * 순간을 잡아 <b>모든 프로젝트의 재발급이 실패</b>한다.
     *
     * <p>여기서는 순서만 못박는다. 인덱스 아래에서 실제로 어떻게 깨지는지는
     * {@code ActiveApiKeyIndexOrderingTest} 가 진짜 DB 로 재현한다.
     */
    @Test
    @DisplayName("비활성화를 flush 한 뒤에 새 키를 저장한다 — 순서가 뒤집히면 인덱스에 걸린다")
    void 비활성화를_먼저_내보낸다() {
        given(projectService.validateOwnershipForUpdate(PROJECT_ID, ACCOUNT_ID)).willReturn(project);
        given(apiKeyRepository.findAllActiveByProjectId(PROJECT_ID)).willReturn(List.of(oldApiKey));
        given(apiKeyGenerator.generateApiKey()).willReturn(NEW_API_KEY);
        given(apiKeyGenerator.generateSecretKey()).willReturn(NEW_SECRET_KEY);
        given(apiKeyRepository.save(any(ApiKey.class))).willAnswer(i -> i.getArgument(0));

        regenerateApiKeyUseCase.execute(ACCOUNT_ID, PROJECT_ID);

        InOrder inOrder = org.mockito.Mockito.inOrder(apiKeyRepository);
        inOrder.verify(apiKeyRepository).findAllActiveByProjectId(PROJECT_ID);
        inOrder.verify(apiKeyRepository).flush();
        inOrder.verify(apiKeyRepository).save(any(ApiKey.class));
    }
}
