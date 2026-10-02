package ai.univs.gate.modules.webhook.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.project.ProjectService;
import ai.univs.gate.support.webhook.WebhookService;
import ai.univs.gate.support.webhook.WebhookTestResult;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UG-344: 토글만 저장 · 테스트 전송")
class WebhookTogglesAndTestUseCaseTest {

    private final ProjectService projectService = mock(ProjectService.class);
    private final WebhookConfigRepository repository = mock(WebhookConfigRepository.class);
    private final WebhookService webhookService = mock(WebhookService.class);
    private final UpdateWebhookTogglesUseCase toggles = new UpdateWebhookTogglesUseCase(projectService, repository);

    /** 테스트가 단조 시계를 움직인다 (나노초). */
    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
    private final SendWebhookTestUseCase test = new SendWebhookTestUseCase(projectService, repository, webhookService, nanos::get);

    private static void 계정(String accountId) {
        UserContext.set(UserContext.builder().accountId(accountId).build());
    }

    @BeforeEach
    void 사용자() {
        UserContext.set(UserContext.builder().accountId("1").timezone("Asia/Seoul").build());
        when(projectService.validateOwnership(anyLong(), anyLong())).thenReturn(mock(Project.class));
        when(projectService.validateOwnershipForUpdate(anyLong(), anyLong())).thenReturn(mock(Project.class));
    }

    @AfterEach
    void 정리() {
        UserContext.clear();
    }

    private static WebhookConfig config(long projectId) {
        return WebhookConfig.builder().id(projectId).project(Project.builder().id(projectId).build())
                .webhookUrl("https://8.8.8.8/h").demoEnabled(false).apiEnabled(true).webhookSecret("whsec_a").build();
    }

    // ── 토글 ──

    @Test
    @DisplayName("준 값만 바꾸고 URL·키는 그대로, 프로젝트 → 설정 순으로 잠근다")
    void 토글_저장() {
        WebhookConfig c = config(10L);
        when(repository.findForUpdateByProjectId(10L)).thenReturn(Optional.of(c));

        var result = toggles.execute(10L, true, null);

        assertThat(result.demoEnabled()).isTrue();
        assertThat(result.apiEnabled()).isTrue();
        assertThat(result.webhookUrl()).isEqualTo("https://8.8.8.8/h");
        assertThat(result.webhookSecret()).isEqualTo("whsec_a");
        verify(projectService).validateOwnershipForUpdate(10L, 1L);
        verify(repository, never()).findByProjectId(anyLong());
    }

    @Test
    @DisplayName("둘 다 없으면 PJ-101, 설정이 없으면 PJ-110")
    void 토글_거절() {
        assertThatThrownBy(() -> toggles.execute(10L, null, null))
                .extracting(e -> ((CustomGateException) e).getErrorType()).isEqualTo(ErrorType.INVALID_INPUT);
        when(repository.findForUpdateByProjectId(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> toggles.execute(10L, true, null))
                .extracting(e -> ((CustomGateException) e).getErrorType()).isEqualTo(ErrorType.WEBHOOK_CONFIG_NOT_FOUND);
    }

    // ── 테스트 전송 ──

    @Test
    @DisplayName("설정이 없으면 PJ-110 이고 보내지 않는다")
    void 테스트_설정_없음() {
        when(repository.findByProjectId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> test.execute(10L))
                .extracting(e -> ((CustomGateException) e).getErrorType()).isEqualTo(ErrorType.WEBHOOK_CONFIG_NOT_FOUND);
        verify(webhookService, never()).sendTest(any(), any());
    }

    @Test
    @DisplayName("반박 리뷰 W2: 소유가 아니면 보내지 않고 간격도 소모하지 않는다")
    void 테스트_소유_확인() {
        when(projectService.validateOwnership(10L, 1L)).thenThrow(new CustomGateException(ErrorType.NOT_OWNERSHIP));

        assertThatThrownBy(() -> test.execute(10L))
                .extracting(e -> ((CustomGateException) e).getErrorType()).isEqualTo(ErrorType.NOT_OWNERSHIP);
        verify(webhookService, never()).sendTest(any(), any());
        verify(repository, never()).findByProjectId(anyLong());

        // 진짜 소유자는 바로 보낼 수 있다 — 거절된 요청이 간격을 남기지 않았다
        when(projectService.validateOwnership(10L, 2L)).thenReturn(mock(Project.class));
        when(repository.findByProjectId(10L)).thenReturn(Optional.of(config(10L)));
        when(webhookService.sendTest(any(), any())).thenReturn(new WebhookTestResult("SUCCESS", 200, 10L, "e"));
        계정("2");
        assertThat(test.execute(10L).result()).isEqualTo("SUCCESS");
        verify(projectService).validateOwnership(10L, 2L);
    }

    @Test
    @DisplayName("같은 프로젝트는 직전 테스트가 끝난 뒤 3초 안에 다시 보낼 수 없다(PJ-112). 지나면 된다. 다른 프로젝트는 상관없다")
    void 테스트_간격() {
        when(repository.findByProjectId(anyLong())).thenAnswer(inv -> Optional.of(config(inv.getArgument(0))));
        // 시도에 2초가 걸린다 — 간격은 시작이 아니라 끝 기준이다
        when(webhookService.sendTest(any(), any())).thenAnswer(inv -> {
            nanos.addAndGet(2_000_000_000L);
            return new WebhookTestResult("SUCCESS", 200, 2000L, "e");
        });

        assertThat(test.execute(10L).result()).isEqualTo("SUCCESS");
        nanos.addAndGet(2_999_000_000L);
        assertThatThrownBy(() -> test.execute(10L))
                .extracting(e -> ((CustomGateException) e).getErrorType()).isEqualTo(ErrorType.WEBHOOK_TEST_TOO_FREQUENT);
        assertThat(test.execute(11L).result()).as("다른 프로젝트").isEqualTo("SUCCESS");
        nanos.addAndGet(1_000_000L - 2_000_000_000L);   // 11번이 쓴 2초를 되돌려 10번 기준 정확히 3초
        assertThat(test.execute(10L).result()).isEqualTo("SUCCESS");
        verify(webhookService, times(3)).sendTest(any(), any());
    }

    /** 응답을 쥐고 있는 sendTest — 들어온 수를 세고, 풀어 줄 때까지 기다린다. */
    private CountDownLatch 붙잡는_전송(CountDownLatch entered) {
        CountDownLatch release = new CountDownLatch(1);
        when(webhookService.sendTest(any(), any())).thenAnswer(inv -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return new WebhookTestResult("SUCCESS", 200, 10L, "e");
        });
        return release;
    }

    @Test
    @DisplayName("반박 리뷰 W1: 같은 계정은 동시에 1건 — 다른 프로젝트로 나눠 눌러도 전체 자리를 차지하지 못한다")
    void 테스트_계정당_동시_1건() throws Exception {
        when(repository.findByProjectId(anyLong())).thenAnswer(inv -> Optional.of(config(inv.getArgument(0))));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = 붙잡는_전송(entered);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = pool.submit(() -> {
                계정("1");
                try {
                    return test.execute(10L);
                } finally {
                    UserContext.clear();
                }
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> test.execute(11L))
                    .extracting(e -> ((CustomGateException) e).getErrorType()).isEqualTo(ErrorType.WEBHOOK_TEST_TOO_FREQUENT);
            verify(webhookService, times(1)).sendTest(any(), any());
            // 다른 계정이 막히지 않는 것은 아래 「전체 동시 실행」 테스트가 본다(계정을 나눠 4건이 동시에 들어간다)
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertThat(test.execute(11L).result()).as("끝나면 같은 계정도 다시 된다").isEqualTo("SUCCESS");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("전체 동시 실행이 4건을 넘으면 PJ-112 — 끝나면 자리가 돌아온다")
    void 테스트_동시_상한() throws Exception {
        when(repository.findByProjectId(anyLong())).thenAnswer(inv -> Optional.of(config(inv.getArgument(0))));
        CountDownLatch entered = new CountDownLatch(SendWebhookTestUseCase.MAX_CONCURRENT);
        CountDownLatch release = 붙잡는_전송(entered);
        ExecutorService pool = Executors.newFixedThreadPool(SendWebhookTestUseCase.MAX_CONCURRENT);
        try {
            Future<?>[] running = new Future<?>[SendWebhookTestUseCase.MAX_CONCURRENT];
            for (int i = 0; i < running.length; i++) {
                long projectId = 100L + i;
                String accountId = String.valueOf(100 + i);   // 계정마다 1건이므로 계정을 나눈다
                running[i] = pool.submit(() -> {
                    계정(accountId);
                    try {
                        return test.execute(projectId);
                    } finally {
                        UserContext.clear();
                    }
                });
            }
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            계정("200");
            assertThatThrownBy(() -> test.execute(200L))
                    .extracting(e -> ((CustomGateException) e).getErrorType()).isEqualTo(ErrorType.WEBHOOK_TEST_TOO_FREQUENT);

            release.countDown();
            for (Future<?> f : running) f.get(5, TimeUnit.SECONDS);
            assertThat(test.execute(200L).result())
                    .as("자리가 돌아왔고, 자리가 없어 거절된 요청은 간격을 남기지 않았다(N1)").isEqualTo("SUCCESS");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("보내는 쪽이 예외를 던져도 동시 실행 자리와 계정 표시는 돌아온다")
    void 테스트_예외_후_반납() {
        when(repository.findByProjectId(anyLong())).thenAnswer(inv -> Optional.of(config(inv.getArgument(0))));
        when(webhookService.sendTest(any(), any())).thenThrow(new IllegalStateException("boom"));

        for (int i = 0; i < SendWebhookTestUseCase.MAX_CONCURRENT + 1; i++) {
            long projectId = 300L + i;
            assertThatThrownBy(() -> test.execute(projectId)).isInstanceOf(IllegalStateException.class);
        }
    }
}
