package ai.univs.gate.support.api_key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.domain.repository.ApiKeyRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.shared.web.enums.ErrorType;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * API 키 조회 캐시 (UG-364). 조회 규칙(UG-281 소유 검증, UG-288 삭제 거부)이 캐시를 거쳐도 그대로인지 본다.
 */
@DisplayName("UG-364: API 키 조회 캐시")
class ApiKeyLookupCacheTest {

    private static final String KEY = "gate_ug364aaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OTHER_KEY = "gate_ug364bbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final long OWNER = 100L;
    private static final long PROJECT = 42L;

    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
    private final ApiKeyLookupCache cache = new ApiKeyLookupCache();
    private final ApiKeyRepository repository = mock(ApiKeyRepository.class);
    private final ApiKeyService service = new ApiKeyService(repository, cache);

    {
        cache.nanoTime = nanos::get;
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static ApiKey key(String value, long projectId) {
        Project project = Project.builder().id(projectId).accountId(OWNER).projectName("p").branchName("b-" + projectId)
                .isDeleted(false).status(ProjectStatus.ACTIVE).build();
        return ApiKey.builder().id(7L).project(project).apiKey(value).secretKey("s").isActive(true).build();
    }

    private void 있다(String value, long projectId) {
        when(repository.findActiveByApiKeyWithLiveProject(value)).thenReturn(Optional.of(key(value, projectId)));
    }

    private void 없다(String value) {
        when(repository.findActiveByApiKeyWithLiveProject(value)).thenReturn(Optional.empty());
    }

    private static void 거부된다(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(CustomGateException.class)
                .extracting(e -> ((CustomGateException) e).getErrorType())
                .isEqualTo(ErrorType.API_KEY_NOT_FOUND);
    }

    @Test
    @DisplayName("한 번 읽은 키는 다시 DB 를 읽지 않는다")
    void 적중하면_DB_를_읽지_않는다() {
        있다(KEY, PROJECT);

        service.findOwnedByApiKey(KEY, OWNER);
        service.findOwnedByApiKey(KEY, OWNER);
        service.findByApiKey(CallerType.DEMO, KEY, null);

        verify(repository, times(1)).findActiveByApiKeyWithLiveProject(KEY);
    }

    @Test
    @DisplayName("다른 키로는 적중하지 않는다 — 캐시 키는 API 키 문자열 그대로다")
    void 다른_키는_따로() {
        있다(KEY, PROJECT);
        없다(OTHER_KEY);
        service.findOwnedByApiKey(KEY, OWNER);

        거부된다(() -> service.findOwnedByApiKey(OTHER_KEY, OWNER));
        verify(repository).findActiveByApiKeyWithLiveProject(OTHER_KEY);
    }

    @Test
    @DisplayName("적중해도 소유 검증은 요청마다 한다 — 남의 계정은 캐시에 있는 키로도 거부된다")
    void 적중해도_소유_검증() {
        있다(KEY, PROJECT);
        service.findOwnedByApiKey(KEY, OWNER);

        거부된다(() -> service.findOwnedByApiKey(KEY, 999L));
        거부된다(() -> service.findByApiKey(CallerType.API, KEY, 999L));
        verify(repository, times(1)).findActiveByApiKeyWithLiveProject(KEY);
    }

    @Test
    @DisplayName("프로젝트를 지우면(커밋 뒤) 그 키는 바로 다시 DB 로 확인해 거부된다 — UG-288")
    void 삭제하면_바로_거부() {
        있다(KEY, PROJECT);
        service.findOwnedByApiKey(KEY, OWNER);

        없다(KEY);   // 삭제가 커밋됐다 — 조회 조건(활성 + 살아 있는 프로젝트)에 걸리지 않는다
        cache.evictProjectAfterCommit(PROJECT);

        거부된다(() -> service.findOwnedByApiKey(KEY, OWNER));
        거부된다(() -> service.findByApiKey(CallerType.DEMO, KEY, null));
    }

    @Test
    @DisplayName("다른 프로젝트를 지워도 이 키는 남는다")
    void 다른_프로젝트_삭제() {
        있다(KEY, PROJECT);
        service.findOwnedByApiKey(KEY, OWNER);

        cache.evictProjectAfterCommit(PROJECT + 1);
        service.findOwnedByApiKey(KEY, OWNER);

        verify(repository, times(1)).findActiveByApiKeyWithLiveProject(KEY);
    }

    @Test
    @DisplayName("트랜잭션 안에서 지우면 끝난 뒤에 지운다 — 끝나기 전에는 그대로")
    void 끝난_뒤에_지운다() {
        있다(KEY, PROJECT);
        service.findOwnedByApiKey(KEY, OWNER);
        TransactionSynchronizationManager.initSynchronization();

        cache.evictProjectAfterCommit(PROJECT);
        assertThat(cache.get(KEY)).as("끝나기 전").isNotNull();

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        assertThat(cache.get(KEY)).as("커밋 뒤").isNull();
    }

    @Test
    @DisplayName("반박 리뷰 M1: 커밋 결과를 모를 때(STATUS_UNKNOWN)도 지운다 — 서버에서는 삭제가 커밋됐을 수 있다")
    void 결과를_몰라도_지운다() {
        있다(KEY, PROJECT);
        service.findOwnedByApiKey(KEY, OWNER);
        TransactionSynchronizationManager.initSynchronization();

        cache.evictProjectAfterCommit(PROJECT);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN));

        assertThat(cache.get(KEY)).isNull();
    }

    @Test
    @DisplayName("읽는 사이에 프로젝트가 지워졌으면 읽은 값을 남기지 않는다 — 삭제한 프로젝트의 키가 캐시 수명 동안 통과하지 않게")
    void 읽는_사이_삭제() {
        // DB 를 읽는 동안(삭제 커밋 직전의 옛 행을 본 뒤) 삭제가 커밋되고 캐시가 지워진다
        when(repository.findActiveByApiKeyWithLiveProject(KEY)).thenAnswer(invocation -> {
            cache.evictProject(PROJECT);
            return Optional.of(key(KEY, PROJECT));
        });

        service.findOwnedByApiKey(KEY, OWNER);   // 이 한 번은 옛 행이라 통과한다 — 삭제 커밋 전에 읽었다

        assertThat(cache.get(KEY)).as("낡은 값은 들어가지 않는다").isNull();
        없다(KEY);
        거부된다(() -> service.findOwnedByApiKey(KEY, OWNER));
    }

    @Test
    @DisplayName("넣기와 지우기가 겹쳐 지우기가 놓친 값도 꺼낼 때 버린다")
    void 꺼낼_때도_세대를_본다() {
        long before = cache.generation();
        cache.evictProject(PROJECT);
        // 지우기 전에 읽은 값이 (지우기가 지나간 뒤) 들어갔다고 친다 — put 의 검사를 비켜 간 경우
        cache.put(KEY, ApiKeySnapshot.of(key(KEY, PROJECT)), cache.generation());
        assertThat(cache.get(KEY)).as("지운 뒤에 읽은 값은 쓴다").isNotNull();

        cache.put(OTHER_KEY, ApiKeySnapshot.of(key(OTHER_KEY, PROJECT)), before);
        assertThat(cache.get(OTHER_KEY)).as("지우기 전에 읽은 값은 쓰지 않는다").isNull();
    }

    @Test
    @DisplayName("없는 키는 기억하지 않는다 — 새로 만든 키는 바로 쓸 수 있다")
    void 없는_키는_기억하지_않는다() {
        없다(KEY);
        거부된다(() -> service.findOwnedByApiKey(KEY, OWNER));

        있다(KEY, PROJECT);
        assertThat(service.findOwnedByApiKey(KEY, OWNER).getProject().getId()).isEqualTo(PROJECT);
    }

    @Test
    @DisplayName("요청마다 새 사본을 준다 — 한 요청이 고친 값이 다른 요청에 번지지 않는다")
    void 사본은_요청마다_새로() {
        있다(KEY, PROJECT);
        ApiKey first = service.findOwnedByApiKey(KEY, OWNER);
        first.getProject().setAccountId(999L);
        first.getProject().setProjectName("changed");

        ApiKey second = service.findOwnedByApiKey(KEY, OWNER);

        assertThat(second).isNotSameAs(first);
        assertThat(second.getProject()).isNotSameAs(first.getProject());
        assertThat(second.getProject().getAccountId()).isEqualTo(OWNER);
        assertThat(second.getProject().getProjectName()).isEqualTo("p");
        assertThat(second.getProject().isDeleted()).isFalse();
        assertThat(second.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("TTL 이 지나면 다시 DB 로 확인한다 — 다른 인스턴스에서 지운 프로젝트도 이 안에 거부된다")
    void TTL_이_지나면_다시_읽는다() {
        있다(KEY, PROJECT);
        service.findOwnedByApiKey(KEY, OWNER);

        nanos.addAndGet(ApiKeyLookupCache.TTL.toNanos() - 1);
        service.findOwnedByApiKey(KEY, OWNER);
        verify(repository, times(1)).findActiveByApiKeyWithLiveProject(anyString());

        nanos.addAndGet(1);
        없다(KEY);
        거부된다(() -> service.findOwnedByApiKey(KEY, OWNER));
    }
}
