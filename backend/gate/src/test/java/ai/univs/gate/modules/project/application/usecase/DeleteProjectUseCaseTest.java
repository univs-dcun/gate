package ai.univs.gate.modules.project.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.domain.repository.ApiKeyRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.project.ProjectService;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * UG-288: 프로젝트 소프트 삭제.
 *
 * <p>{@code Project.delete()} 는 {@code isDeleted = false} 를 세팅하고 있었다. 컬럼 기본값이
 * {@code FALSE} 라 아무 일도 하지 않는 줄이었고, 그래서 <b>프로젝트 삭제는 한 번도 동작한 적이
 * 없었다</b> — 삭제해도 목록에 남고, 그 프로젝트의 API 키로 등록·매칭·이력 조회가 전부 됐다.
 *
 * <p>이런 종류의 결함은 컴파일러도 기존 테스트도 잡지 못한다. 삭제 경로에 테스트가 하나도 없었기
 * 때문이다. 그래서 "삭제하면 무엇이 참이 되는가" 를 여기서 못박는다.
 *
 * <p>영속성 경계(트랜잭션·flush)는 여기서 볼 수 없다 — 순수 Mockito 테스트다. 그쪽은
 * {@link DeleteProjectTransactionGuardTest} 가 맡는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-288: 프로젝트 삭제")
class DeleteProjectUseCaseTest {

    private static final long ACCOUNT = 7L;
    private static final long PROJECT = 42L;

    @Mock
    private ProjectService projectService;

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @InjectMocks
    private DeleteProjectUseCase deleteProjectUseCase;

    private Project project;
    private ApiKey apiKey;

    @BeforeEach
    void setUp() {
        project = Project.builder()
                .accountId(ACCOUNT)
                .status(ProjectStatus.ACTIVE)
                .isDeleted(false)
                .build();
        ReflectionTestUtils.setField(project, "id", PROJECT);

        apiKey = activeKey(9L, "univs_live_abcdefghijklmnop");
    }

    private ApiKey activeKey(long id, String value) {
        ApiKey key = ApiKey.builder().project(project).apiKey(value).isActive(true).build();
        ReflectionTestUtils.setField(key, "id", id);
        return key;
    }

    private void givenOwnedProject() {
        given(projectService.validateOwnership(PROJECT, ACCOUNT)).willReturn(project);
    }

    private void givenActiveKeys(ApiKey... keys) {
        given(apiKeyRepository.findAllActiveByProjectId(PROJECT)).willReturn(List.of(keys));
    }

    @Test
    @DisplayName("삭제 플래그가 실제로 켜진다")
    void 삭제_플래그가_켜진다() {
        // 원래 버그가 정확히 여기였다. delete() 가 isDeleted 를 false 로 두면 이 단언이 깨진다.
        givenOwnedProject();
        givenActiveKeys(apiKey);

        deleteProjectUseCase.execute(ACCOUNT, PROJECT);

        assertThat(project.isDeleted())
                .as("이 플래그가 켜지지 않으면 목록 조회·findByIdAndIsDeletedFalse 가 삭제를 못 본다")
                .isTrue();
    }

    @Test
    @DisplayName("상태가 DELETED 가 된다 — INACTIVE 가 아니다")
    void 상태가_DELETED() {
        // ProjectStatus.DELETED 는 정의만 되고 어디서도 쓰이지 않던 값이었다. gate-web 은
        // 'ACTIVE' | 'INACTIVE' | 'DELETED' 로 이미 기대하고 있다. INACTIVE 는 '삭제는 아니지만
        // 비활성' 자리로 남긴다 — 둘을 같은 값으로 쓰면 그 구분을 영영 못 만든다.
        givenOwnedProject();
        givenActiveKeys(apiKey);

        deleteProjectUseCase.execute(ACCOUNT, PROJECT);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.DELETED);
    }

    @Test
    @DisplayName("API 키가 함께 비활성화된다")
    void API_키가_비활성화된다() {
        givenOwnedProject();
        givenActiveKeys(apiKey);

        deleteProjectUseCase.execute(ACCOUNT, PROJECT);

        assertThat(apiKey.getIsActive())
                .as("키가 살아 있으면 삭제된 프로젝트로 등록·매칭이 계속 된다")
                .isFalse();
    }

    @Test
    @DisplayName("활성 키가 여러 개여도 전부 끄고 삭제된다")
    void 활성_키가_둘이어도_삭제된다() {
        // 반박 리뷰 지적. api_keys 에는 (project_id, is_active) 부분 유니크 인덱스가 없고,
        // RegenerateApiKeyUseCase 가 잠금 없이 '기존 끄기 → 새로 넣기' 를 하므로 동시 호출이면
        // 활성 키 2개가 남을 수 있다. Optional 조회를 쓰면 그 프로젝트는
        // IncorrectResultSizeDataAccessException 으로 삭제까지 롤백돼 영영 지울 수 없게 된다.
        ApiKey second = activeKey(10L, "univs_live_qrstuvwxyz012345");
        givenOwnedProject();
        givenActiveKeys(apiKey, second);

        deleteProjectUseCase.execute(ACCOUNT, PROJECT);

        assertThat(apiKey.getIsActive()).isFalse();
        assertThat(second.getIsActive()).isFalse();
        assertThat(project.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("활성 키가 없어도 삭제는 성공한다")
    void 활성_키가_없어도_터지지_않는다() {
        // 키를 회전하다 중단됐거나 이미 비활성화된 프로젝트가 있을 수 있다. 그 경우에
        // 삭제 자체가 실패하면 사용자는 프로젝트를 영영 못 지운다.
        givenOwnedProject();
        givenActiveKeys();

        assertThatCode(() -> deleteProjectUseCase.execute(ACCOUNT, PROJECT))
                .doesNotThrowAnyException();
        assertThat(project.isDeleted()).isTrue();
    }

    /**
     * 비활성화 기록이 남는지 (델타 리뷰 지적).
     *
     * <p>{@code if (!activeKeys.isEmpty())} 조건을 뒤집어도 — 즉 키를 껐을 때는 조용하고 아무것도
     * 안 껐을 때만 로그를 남기도록 바꿔도 — 기존 테스트가 전부 초록이었다. 이 로그는 "언제 어떤
     * 키가 꺼졌는가" 의 유일한 흔적이다. 삭제된 프로젝트의 키로 호출이 들어오면
     * {@code ApiKeyService} 가 WARN 을 남기는데, 그때 이 줄이 없으면 그 키가 어쩌다 살아남았는지
     * 추적할 방법이 없다.
     */
    @Test
    @DisplayName("끈 키의 id 를 로그로 남긴다 — 끌 게 없으면 남기지 않는다")
    void 비활성화_기록이_남는다() {
        Logger logger = (Logger) LoggerFactory.getLogger(DeleteProjectUseCase.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            givenOwnedProject();
            givenActiveKeys(apiKey);

            deleteProjectUseCase.execute(ACCOUNT, PROJECT);

            assertThat(appender.list)
                    .as("어떤 키가 꺼졌는지 남아야 한다")
                    .anyMatch(event -> event.getFormattedMessage().contains("9"));

            appender.list.clear();

            // 끌 키가 없으면 굳이 남기지 않는다. 조건이 뒤집히면 이쪽이 깨진다.
            Project 다른프로젝트 = Project.builder().accountId(ACCOUNT).isDeleted(false).build();
            ReflectionTestUtils.setField(다른프로젝트, "id", 43L);
            given(projectService.validateOwnership(43L, ACCOUNT)).willReturn(다른프로젝트);
            given(apiKeyRepository.findAllActiveByProjectId(43L)).willReturn(List.of());

            deleteProjectUseCase.execute(ACCOUNT, 43L);

            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("소유자가 아니면 아무것도 바꾸지 않는다")
    void 타계정은_아무것도_못_바꾼다() {
        willThrow(new CustomGateException(ErrorType.NOT_OWNERSHIP))
                .given(projectService).validateOwnership(PROJECT, ACCOUNT);

        assertThatThrownBy(() -> deleteProjectUseCase.execute(ACCOUNT, PROJECT))
                .isInstanceOf(CustomGateException.class);

        assertThat(project.isDeleted()).isFalse();
        assertThat(apiKey.getIsActive()).isTrue();
        verify(apiKeyRepository, never()).findAllActiveByProjectId(PROJECT);
    }
}
