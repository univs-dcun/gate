package ai.univs.gate.modules.webhook.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.webhook.application.input.UpsertWebhookConfigInput;
import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.support.project.ProjectService;
import ai.univs.gate.support.webhook.WebhookProperties;
import ai.univs.gate.support.webhook.WebhookTargetPolicy;
import ai.univs.gate.support.webhook.WebhookToggleCache;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 웹훅 설정을 바꾸는 쪽이 전송 쪽 토글 캐시를 지우는가 (UG-361). 지우지 않으면 웹훅을 켠 뒤에도 이 인스턴스는 최대 10초
 * 동안 낡은 「꺼짐」으로 이벤트를 버린다.
 */
@DisplayName("UG-361: 설정 저장·토글·삭제는 토글 캐시를 지운다")
class WebhookToggleCacheEvictionTest {

    private final ProjectService projectService = mock(ProjectService.class);
    private final WebhookConfigRepository repository = mock(WebhookConfigRepository.class);
    private final WebhookToggleCache cache = mock(WebhookToggleCache.class);
    private final WebhookTargetPolicy policy = new WebhookTargetPolicy(new WebhookProperties(
            false, Duration.ofSeconds(3), Duration.ofSeconds(5), 3, Duration.ofSeconds(1), 50, 500, 1000, List.of()));

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

    private static WebhookConfig config() {
        return WebhookConfig.builder().id(9L).project(Project.builder().id(10L).build()).webhookUrl("https://8.8.8.8/h")
                .demoEnabled(false).apiEnabled(false).webhookSecret("whsec_a").build();
    }

    @Test
    @DisplayName("새로 저장")
    void 신규_저장() {
        when(repository.findForUpdateByProjectId(10L)).thenReturn(Optional.empty());

        new UpsertWebhookConfigUseCase(projectService, repository, policy, cache)
                .execute(new UpsertWebhookConfigInput(1L, 10L, "https://8.8.8.8/hook", false, true));

        verify(cache).evictAfterCommit(10L);
    }

    @Test
    @DisplayName("기존 설정 수정")
    void 수정() {
        when(repository.findForUpdateByProjectId(10L)).thenReturn(Optional.of(config()));

        new UpsertWebhookConfigUseCase(projectService, repository, policy, cache)
                .execute(new UpsertWebhookConfigInput(1L, 10L, "https://8.8.8.8/hook", false, true));

        verify(cache).evictAfterCommit(10L);
    }

    @Test
    @DisplayName("거절된 저장은 지우지 않는다 — 바뀐 것이 없다")
    void 거절() {
        assertThatThrownBy(() -> new UpsertWebhookConfigUseCase(projectService, repository, policy, cache)
                .execute(new UpsertWebhookConfigInput(1L, 10L, "http://127.0.0.1:7432/", false, true)))
                .isInstanceOf(CustomGateException.class);

        verify(cache, never()).evictAfterCommit(anyLong());
    }

    @Test
    @DisplayName("토글만 저장")
    void 토글() {
        when(repository.findForUpdateByProjectId(10L)).thenReturn(Optional.of(config()));

        new UpdateWebhookTogglesUseCase(projectService, repository, cache).execute(10L, null, true);

        verify(cache).evictAfterCommit(10L);
    }

    @Test
    @DisplayName("삭제")
    void 삭제() {
        when(repository.findByProjectId(10L)).thenReturn(Optional.of(config()));

        new DeleteWebhookConfigUseCase(projectService, repository, cache).execute(10L);

        verify(cache).evictAfterCommit(10L);
    }
}
