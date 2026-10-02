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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UG-111: 웹훅 설정 저장 시 URL 검사")
class UpsertWebhookConfigUseCaseTest {

    private final ProjectService projectService = mock(ProjectService.class);
    private final WebhookConfigRepository repository = mock(WebhookConfigRepository.class);
    private final WebhookTargetPolicy policy = new WebhookTargetPolicy(new WebhookProperties(
            false, Duration.ofSeconds(3), Duration.ofSeconds(5), 3, Duration.ofSeconds(1), 50, 500, 1000, List.of()));
    private final UpsertWebhookConfigUseCase useCase = new UpsertWebhookConfigUseCase(projectService, repository, policy);

    @BeforeEach
    void 소유() {
        when(projectService.validateOwnership(10L, 1L)).thenReturn(mock(Project.class));
        when(projectService.validateOwnershipForUpdate(10L, 1L)).thenReturn(mock(Project.class));
    }

    @Test
    @DisplayName("내부 주소는 저장하지 않고 PJ-111 로 거절한다 — 새로 만들 때")
    void 신규_거절() {
        when(repository.findForUpdateByProjectId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(input("http://172.18.0.1:7888/actuator")))
                .isInstanceOf(CustomGateException.class)
                .extracting(e -> ((CustomGateException) e).getErrorType())
                .isEqualTo(ErrorType.WEBHOOK_URL_NOT_ALLOWED);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("2차 반박 리뷰 W-b: 막힌 주소는 프로젝트 행을 잠그기 전에 거절한다 — DNS 조회 동안 잠금을 쥐지 않는다")
    void 잠금_전에_URL_검사() {
        assertThatThrownBy(() -> useCase.execute(input("http://127.0.0.1:7432/")))
                .isInstanceOf(CustomGateException.class);
        verify(projectService, never()).validateOwnershipForUpdate(any(), any());
    }

    @Test
    @DisplayName("기존 설정을 내부 주소로 바꾸려 해도 거절하고 기존 값을 건드리지 않는다")
    void 수정_거절() {
        WebhookConfig existing = WebhookConfig.builder()
                .webhookUrl("https://8.8.8.8/hook").demoEnabled(true).apiEnabled(true).build();
        when(repository.findForUpdateByProjectId(10L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(input("http://127.0.0.1:7432/")))
                .isInstanceOf(CustomGateException.class);
        assertThat(existing.getWebhookUrl()).isEqualTo("https://8.8.8.8/hook");
    }

    @Test
    @DisplayName("공인 주소는 저장한다")
    void 저장() {
        when(repository.findForUpdateByProjectId(10L)).thenReturn(Optional.empty());

        assertThat(useCase.execute(input("https://8.8.8.8/hook")).webhookUrl()).isEqualTo("https://8.8.8.8/hook");
        verify(repository).save(any());
    }

    @Test
    @DisplayName("UG-344: 새 설정에는 서명 키가 발급된다")
    void 신규_키() {
        when(repository.findForUpdateByProjectId(10L)).thenReturn(Optional.empty());

        assertThat(useCase.execute(input("https://8.8.8.8/hook")).webhookSecret()).startsWith("whsec_");
        // 반박 리뷰 W1: 설정이 없을 때도 직렬화되도록 프로젝트 행을 잠근다
        verify(projectService).validateOwnershipForUpdate(10L, 1L);
    }

    @Test
    @DisplayName("UG-344: 키가 없던 기존 설정은 저장할 때 채우고, 있던 키는 바꾸지 않는다")
    void 기존_키() {
        WebhookConfig 없음 = WebhookConfig.builder().project(Project.builder().id(10L).build())
                .webhookUrl("https://8.8.8.8/a").demoEnabled(true).apiEnabled(true).build();
        when(repository.findForUpdateByProjectId(10L)).thenReturn(Optional.of(없음));
        assertThat(useCase.execute(input("https://8.8.8.8/hook")).webhookSecret()).startsWith("whsec_");

        WebhookConfig 있음 = WebhookConfig.builder().project(Project.builder().id(10L).build())
                .webhookUrl("https://8.8.8.8/a").demoEnabled(true).apiEnabled(true).webhookSecret("whsec_keep").build();
        when(repository.findForUpdateByProjectId(10L)).thenReturn(Optional.of(있음));
        assertThat(useCase.execute(input("https://8.8.8.8/hook")).webhookSecret()).isEqualTo("whsec_keep");
    }

    private static UpsertWebhookConfigInput input(String url) {
        return new UpsertWebhookConfigInput(1L, 10L, url, true, true);
    }
}
