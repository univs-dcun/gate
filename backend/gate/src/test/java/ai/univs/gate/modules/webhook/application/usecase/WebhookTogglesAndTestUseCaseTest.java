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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

    /** 테스트가 시각을 움직인다. */
    private Instant now = Instant.parse("2026-10-02T00:00:00Z");
    private final Clock clock = new Clock() {
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    };
    private final SendWebhookTestUseCase test = new SendWebhookTestUseCase(projectService, repository, webhookService, clock);

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
    @DisplayName("같은 프로젝트는 3초 안에 다시 보낼 수 없다(PJ-112). 3초가 지나면 된다. 다른 프로젝트는 상관없다")
    void 테스트_간격() {
        when(repository.findByProjectId(anyLong())).thenAnswer(inv -> Optional.of(config(inv.getArgument(0))));
        when(webhookService.sendTest(any(), any())).thenReturn(new WebhookTestResult("SUCCESS", 200, 10L, "e"));

        assertThat(test.execute(10L).result()).isEqualTo("SUCCESS");
        now = now.plusMillis(2_999);
        assertThatThrownBy(() -> test.execute(10L))
                .extracting(e -> ((CustomGateException) e).getErrorType()).isEqualTo(ErrorType.WEBHOOK_TEST_TOO_FREQUENT);
        assertThat(test.execute(11L).result()).as("다른 프로젝트").isEqualTo("SUCCESS");
        now = now.plusMillis(1);
        assertThat(test.execute(10L).result()).isEqualTo("SUCCESS");
        verify(webhookService, times(3)).sendTest(any(), any());
    }

    @Test
    @DisplayName("전체 동시 실행이 4건을 넘으면 PJ-112 — 끝나면 자리가 돌아온다")
    void 테스트_동시_상한() throws Exception {
        when(repository.findByProjectId(anyLong())).thenAnswer(inv -> Optional.of(config(inv.getArgument(0))));
        CountDownLatch entered = new CountDownLatch(SendWebhookTestUseCase.MAX_CONCURRENT);
        CountDownLatch release = new CountDownLatch(1);
        when(webhookService.sendTest(any(), any())).thenAnswer(inv -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return new WebhookTestResult("SUCCESS", 200, 10L, "e");
        });
        ExecutorService pool = Executors.newFixedThreadPool(SendWebhookTestUseCase.MAX_CONCURRENT);
        try {
            Future<?>[] running = new Future<?>[SendWebhookTestUseCase.MAX_CONCURRENT];
            for (int i = 0; i < running.length; i++) {
                long projectId = 100L + i;
                running[i] = pool.submit(() -> {
                    UserContext.set(UserContext.builder().accountId("1").build());
                    try {
                        return test.execute(projectId);
                    } finally {
                        UserContext.clear();
                    }
                });
            }
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> test.execute(200L))
                    .extracting(e -> ((CustomGateException) e).getErrorType()).isEqualTo(ErrorType.WEBHOOK_TEST_TOO_FREQUENT);

            release.countDown();
            for (Future<?> f : running) f.get(5, TimeUnit.SECONDS);
            assertThat(test.execute(201L).result()).as("자리가 돌아왔다").isEqualTo("SUCCESS");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("보내는 쪽이 예외를 던져도 동시 실행 자리는 돌아온다")
    void 테스트_예외_후_반납() {
        when(repository.findByProjectId(anyLong())).thenAnswer(inv -> Optional.of(config(inv.getArgument(0))));
        when(webhookService.sendTest(any(), any())).thenThrow(new IllegalStateException("boom"));

        for (int i = 0; i < SendWebhookTestUseCase.MAX_CONCURRENT + 1; i++) {
            long projectId = 300L + i;
            assertThatThrownBy(() -> test.execute(projectId)).isInstanceOf(IllegalStateException.class);
        }
    }
}
