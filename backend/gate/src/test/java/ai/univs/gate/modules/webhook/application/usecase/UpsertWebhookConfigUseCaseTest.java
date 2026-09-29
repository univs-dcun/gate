package ai.univs.gate.modules.webhook.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.webhook.application.input.UpsertWebhookConfigInput;
import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.project.ProjectService;
import ai.univs.gate.support.webhook.WebhookProperties;
import ai.univs.gate.support.webhook.WebhookTargetPolicy;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UG-111: 웹훅 설정 저장 시 URL 검사")
class UpsertWebhookConfigUseCaseTest {

    private final ProjectService projectService = mock(ProjectService.class);
    private final WebhookConfigRepository repository = mock(WebhookConfigRepository.class);
    private final WebhookTargetPolicy policy = new WebhookTargetPolicy(new WebhookProperties(
            false, Duration.ofSeconds(3), Duration.ofSeconds(5), 3, Duration.ofSeconds(1), 50, 500, 1000));
    private final UpsertWebhookConfigUseCase useCase = new UpsertWebhookConfigUseCase(projectService, repository, policy);

    @BeforeEach
    void 소유() {
        when(projectService.validateOwnership(10L, 1L)).thenReturn(mock(Project.class));
    }

    @Test
    @DisplayName("내부 주소는 저장하지 않고 PJ-111 로 거절한다 — 새로 만들 때")
    void 신규_거절() {
        when(repository.findByProjectId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(input("http://172.18.0.1:7888/actuator")))
                .isInstanceOf(CustomGateException.class)
                .extracting(e -> ((CustomGateException) e).getErrorType())
                .isEqualTo(ErrorType.WEBHOOK_URL_NOT_ALLOWED);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("기존 설정을 내부 주소로 바꾸려 해도 거절하고 기존 값을 건드리지 않는다")
    void 수정_거절() {
        WebhookConfig existing = WebhookConfig.builder()
                .webhookUrl("https://8.8.8.8/hook").demoEnabled(true).apiEnabled(true).build();
        when(repository.findByProjectId(10L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(input("http://127.0.0.1:7432/")))
                .isInstanceOf(CustomGateException.class);
        assertThat(existing.getWebhookUrl()).isEqualTo("https://8.8.8.8/hook");
    }

    @Test
    @DisplayName("공인 주소는 저장한다")
    void 저장() {
        when(repository.findByProjectId(10L)).thenReturn(Optional.empty());

        assertThat(useCase.execute(input("https://8.8.8.8/hook")).webhookUrl()).isEqualTo("https://8.8.8.8/hook");
        verify(repository).save(any());
    }

    private static UpsertWebhookConfigInput input(String url) {
        return new UpsertWebhookConfigInput(1L, 10L, url, true, true);
    }
}
