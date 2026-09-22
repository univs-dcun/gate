package ai.univs.gate.support.api_key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.domain.repository.ApiKeyRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.shared.web.enums.ErrorType;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Optional;
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
 * UG-288: 삭제된 프로젝트의 API 키는 어느 경로로도 통하지 않는다.
 *
 * <p>{@code DeleteProjectUseCase} 가 삭제 시 키를 비활성화하지만, 그것만으로는 부족하다. 그 경로를
 * 타지 않고 {@code is_deleted} 가 켜진 행(직접 DB 수정, 배치, 앞으로 생길 다른 삭제 경로)이 있으면
 * 키가 그대로 유효해진다. 그래서 <b>조회 시점에도</b> 막는다.
 *
 * <p>검사 지점은 <b>두 곳</b>이다. 키 문자열로 조회하는 세 메서드는 전부
 * {@code findByApiKeyUnverified} 를 거치므로 거기 한 번, 그 경로 밖인 {@code findByProject} 에
 * 한 번. 이 테스트가 네 진입점을 모두 두드리는 것은 그 구조가 유지되는지 확인하기 위해서다 —
 * 누군가 {@code findOwnedByApiKey} 를 리포지토리 직행으로 바꾸면 여기서 걸린다.
 *
 * <p>(이 문단은 "한 곳" 이라고 적혀 있었다. 델타 리뷰가 짚었듯 같은 커밋이 두 번째 지점을
 * 만들었으므로 사실이 아니었다.)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-288: 삭제된 프로젝트의 API 키")
class ApiKeyDeletedProjectTest {

    private static final long OWNER = 100L;
    private static final String KEY = "univs_live_abcdefghijklmnop";

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @InjectMocks
    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(apiKeyService, "modeProperty", "ENFORCE");
    }

    private void givenKeyOfProject(boolean projectDeleted) {
        Project project = Project.builder().accountId(OWNER).isDeleted(projectDeleted).build();
        ReflectionTestUtils.setField(project, "id", 42L);

        ApiKey apiKey = ApiKey.builder().project(project).apiKey(KEY).isActive(true).build();

        // UG-300: 규칙이 쿼리로 내려갔으므로 스텁도 진짜 리포지토리처럼 굴어야 한다.
        // 삭제된 프로젝트면 살아있는-프로젝트 조회는 비고, 진단용 조회에서만 행이 나온다.
        given(apiKeyRepository.findActiveByApiKeyWithLiveProject(KEY))
                .willReturn(projectDeleted ? Optional.empty() : Optional.of(apiKey));
        if (projectDeleted) {
            given(apiKeyRepository.findByApiKeyAndIsActiveTrue(KEY)).willReturn(Optional.of(apiKey));
        }
    }

    private ErrorType errorTypeOf(Runnable call) {
        try {
            call.run();
            throw new AssertionError("예외가 발생하지 않았다");
        } catch (CustomGateException e) {
            return e.getErrorType();
        }
    }

    @Test
    @DisplayName("인증 경로 — 소유자가 불러도 거부한다")
    void 인증경로에서_거부() {
        givenKeyOfProject(true);

        assertThatThrownBy(() -> apiKeyService.findOwnedByApiKey(KEY, OWNER))
                .isInstanceOf(CustomGateException.class);
    }

    @Test
    @DisplayName("데모 경로도 예외가 아니다")
    void 데모경로에서도_거부() {
        // 소유 검증은 데모를 면제한다 — 대조할 accountId 가 없기 때문이다. 삭제 검사는 다르다.
        // "데모 키가 공개돼도 되는" 근거는 그 키로 할 수 있는 일이 데모 범위에 머문다는 것인데,
        // 삭제된 프로젝트에는 머물 범위 자체가 없다. 여기서 면제하면 UG-286 이 지적한 무인증
        // 목록·등록 경로가 삭제 후에도 살아 있게 된다.
        givenKeyOfProject(true);

        assertThatThrownBy(() -> apiKeyService.findByApiKey(CallerType.DEMO, KEY, 0L))
                .isInstanceOf(CustomGateException.class);
    }

    @Test
    @DisplayName("공유 UseCase 의 API 경로도 거부한다")
    void 공유_UseCase_API경로에서도_거부() {
        givenKeyOfProject(true);

        assertThatThrownBy(() -> apiKeyService.findByApiKey(CallerType.API, KEY, OWNER))
                .isInstanceOf(CustomGateException.class);
    }

    @Test
    @DisplayName("LOG_ONLY 여도 삭제된 프로젝트는 거부한다")
    void LOG_ONLY_는_이_검사를_끄지_않는다() {
        // LOG_ONLY 는 UG-281 의 '소유 검증' 만 되돌리는 스위치다. 여기까지 함께 꺼지면
        // 되돌림 스위치 하나가 서로 다른 두 통제를 동시에 무력화하게 된다.
        ReflectionTestUtils.setField(apiKeyService, "modeProperty", "LOG_ONLY");
        givenKeyOfProject(true);

        assertThatThrownBy(() -> apiKeyService.findOwnedByApiKey(KEY, OWNER))
                .isInstanceOf(CustomGateException.class);
    }

    @Test
    @DisplayName("없는 키와 같은 오류 코드다 — 열거 오라클 방지")
    void 열거_오라클_없음() {
        given(apiKeyRepository.findActiveByApiKeyWithLiveProject("없는키")).willReturn(Optional.empty());
        given(apiKeyRepository.findByApiKeyAndIsActiveTrue("없는키")).willReturn(Optional.empty());
        givenKeyOfProject(true);

        ErrorType 없는키 = errorTypeOf(() -> apiKeyService.findOwnedByApiKey("없는키", OWNER));
        ErrorType 삭제된프로젝트의키 = errorTypeOf(() -> apiKeyService.findOwnedByApiKey(KEY, OWNER));

        // 코드가 갈리면 "이 키는 실재했다" 를 알려주는 셈이 된다. UG-281·UG-250 과 같은 논리.
        assertThat(삭제된프로젝트의키).isEqualTo(없는키).isEqualTo(ErrorType.API_KEY_NOT_FOUND);
    }

    @Test
    @DisplayName("거부 로그에 API 키 원문을 남기지 않는다")
    void 로그에_키_원문이_없다() {
        // 반박 리뷰가 찾은 생존 변이. ApiKeyMasker.mask 를 빼고 원문을 찍어도 아무 테스트도
        // 깨지지 않았다. API 키는 특징점 등록·매칭 전 기능의 인증 수단이라, 로그 열람 권한만으로
        // 남의 생체 API 를 호출할 수 있게 된다. 온프레미스에서는 로그 묶음이 그대로 밖으로 나간다.
        // (UG-274 가 ApiKeyMasker 를 만든 이유와 같다)
        Logger logger = (Logger) LoggerFactory.getLogger(ApiKeyService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            givenKeyOfProject(true);

            assertThatThrownBy(() -> apiKeyService.findOwnedByApiKey(KEY, OWNER))
                    .isInstanceOf(CustomGateException.class);

            assertThat(appender.list)
                    .as("조사 단서가 남아야 하므로 로그 자체는 있어야 한다")
                    .isNotEmpty();
            assertThat(appender.list)
                    .noneMatch(event -> event.getFormattedMessage().contains(KEY));

            // UG-300 반박 리뷰: 로그가 '있다' 만 보면 내용이 비어도 통과한다. 운영자가 어떤
            // 프로젝트인지 찾을 수 있어야 이 진단이 의미가 있다 — 그게 규칙을 쿼리로 옮기면서
            // 굳이 실패 경로 조회를 남긴 이유다.
            assertThat(appender.list)
                    .as("어떤 프로젝트인지 없으면 조사 단서가 되지 못한다")
                    .anyMatch(event -> event.getFormattedMessage().contains("42"));
            assertThat(appender.list)
                    .as("삭제 여부가 없으면 '고아 키' 와 '삭제된 프로젝트' 를 구분할 수 없다")
                    .anyMatch(event -> event.getFormattedMessage().contains("deleted=true"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    /**
     * 프로젝트 행이 사라진 고아 키 (UG-300 반박 리뷰 지적).
     *
     * <p>{@code api_keys.project_id} 에는 외래 키 제약이 없다 (V1 확인). 그래서 프로젝트 행만
     * 사라진 상태가 물리적으로 가능하고, 그 지연 프록시를 건드리면
     * {@code EntityNotFoundException} 이 난다.
     *
     * <p>UG-300 이 삭제 검사를 쿼리로 옮기면서 그 프록시 접근이 <b>실패 경로의 진단</b>으로
     * 옮겨졌다. 거기서 예외가 새어 나가면 "없는 키" 는 400, "고아 키" 는 500 이 되어
     * 이 클래스가 세 문단에 걸쳐 피하려는 열거 오라클이 진단 코드 때문에 생긴다.
     */
    @Test
    @DisplayName("프로젝트 행이 사라진 키도 없는 키와 같은 응답이다 — 진단이 응답을 바꾸지 않는다")
    void 고아_키도_같은_응답() {
        ApiKey orphan = org.mockito.Mockito.mock(ApiKey.class);
        given(orphan.getProject())
                .willThrow(new jakarta.persistence.EntityNotFoundException("project 42 없음"));

        given(apiKeyRepository.findActiveByApiKeyWithLiveProject(KEY)).willReturn(Optional.empty());
        given(apiKeyRepository.findByApiKeyAndIsActiveTrue(KEY)).willReturn(Optional.of(orphan));
        given(apiKeyRepository.findActiveByApiKeyWithLiveProject("없는키")).willReturn(Optional.empty());
        given(apiKeyRepository.findByApiKeyAndIsActiveTrue("없는키")).willReturn(Optional.empty());

        ErrorType 고아키 = errorTypeOf(() -> apiKeyService.findOwnedByApiKey(KEY, OWNER));
        ErrorType 없는키 = errorTypeOf(() -> apiKeyService.findOwnedByApiKey("없는키", OWNER));

        assertThat(고아키)
                .as("진단이 예외를 흘리면 여기서 500 이 되어 '이 키는 실재한다' 를 알려준다")
                .isEqualTo(없는키)
                .isEqualTo(ErrorType.API_KEY_NOT_FOUND);
    }

    @Test
    @DisplayName("살아 있는 프로젝트는 그대로 통과한다")
    void 살아있는_프로젝트는_통과() {
        // 대조군. 이게 없으면 "전부 거부" 로 바뀌어도 위 테스트들은 초록이다.
        givenKeyOfProject(false);

        assertThat(apiKeyService.findOwnedByApiKey(KEY, OWNER)).isNotNull();
        assertThat(apiKeyService.findByApiKey(CallerType.DEMO, KEY, 0L)).isNotNull();
        assertThat(apiKeyService.findByApiKeyUnverified(KEY)).isNotNull();
    }

    private Project project(boolean deleted) {
        Project project = Project.builder().accountId(OWNER).isDeleted(deleted).build();
        ReflectionTestUtils.setField(project, "id", 42L);
        return project;
    }

    /**
     * 두 번째 검사 지점 (델타 리뷰 지적).
     *
     * <p>이 가드는 처음 넣었을 때 어떤 테스트도 닿지 않았다 — 통째로 지워도 전 테스트가 초록이었다.
     * 같은 클래스의 javadoc 이 "보안 통제를 어떤 테스트도 닿지 않는 자리에 두지 않으려고" 라고
     * 적어 놓고 바로 그런 자리를 하나 더 만든 셈이었다.
     */
    @Test
    @DisplayName("프로젝트로 조회하는 경로도 삭제된 프로젝트를 거부한다")
    void 프로젝트_조회경로에서도_거부() {
        Logger logger = (Logger) LoggerFactory.getLogger(ApiKeyService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            assertThatThrownBy(() -> apiKeyService.findByProject(project(true)))
                    .isInstanceOf(CustomGateException.class);

            // 예외만으로는 이 가드를 못 지킨다. 가드를 통째로 지워도 아래 리포지토리 조회가
            // 빈 Optional 을 돌려주며 같은 API_KEY_NOT_FOUND 를 던지기 때문이다 (델타 리뷰의
            // R1 변이가 그렇게 살아남았다). "리포지토리까지 가지 않는다" 와 "흔적을 남긴다" 로
            // 못박는다.
            verify(apiKeyRepository, never()).findLatestActiveByProjectId(anyLong());
            assertThat(appender.list)
                    .as("삭제된 프로젝트로 키를 찾으려 한 것은 조사 단서다")
                    .anyMatch(event -> event.getFormattedMessage().contains("42"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("프로젝트 조회 경로의 오류 코드도 키 없음과 같다 — 열거 오라클 방지")
    void 프로젝트_조회경로_열거_오라클_없음() {
        // 처음에는 PROJECT_NOT_FOUND 를 던졌다. 그러면 호출자가 "삭제된 프로젝트" 와 "키 없음" 을
        // 응답으로 구분할 수 있어, 이 클래스가 findByApiKeyUnverified 에서 세 문단에 걸쳐 피한
        // 열거 오라클을 이 메서드만 다시 만든다.
        Project 살아있음 = project(false);
        given(apiKeyRepository.findLatestActiveByProjectId(42L)).willReturn(Optional.empty());

        ErrorType 키없음 = errorTypeOf(() -> apiKeyService.findByProject(살아있음));
        ErrorType 삭제된프로젝트 = errorTypeOf(() -> apiKeyService.findByProject(project(true)));

        assertThat(삭제된프로젝트).isEqualTo(키없음).isEqualTo(ErrorType.API_KEY_NOT_FOUND);
    }

    @Test
    @DisplayName("살아 있는 프로젝트는 프로젝트 조회 경로로도 통과한다")
    void 프로젝트_조회경로_대조군() {
        Project 살아있음 = project(false);
        ApiKey apiKey = ApiKey.builder().project(살아있음).apiKey(KEY).isActive(true).build();
        given(apiKeyRepository.findLatestActiveByProjectId(42L)).willReturn(Optional.of(apiKey));

        assertThat(apiKeyService.findByProject(살아있음)).isSameAs(apiKey);
    }
}
