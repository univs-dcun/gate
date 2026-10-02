package ai.univs.gate.modules.webhook.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.webhook.application.result.WebhookConfigResult;
import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.project.ProjectService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UG-344: 서명 키 조회·재발급")
class WebhookSecretUseCaseTest {

    private final ProjectService projectService = mock(ProjectService.class);
    private final WebhookConfigRepository repository = mock(WebhookConfigRepository.class);
    private final GetWebhookConfigUseCase get = new GetWebhookConfigUseCase(projectService, repository);
    private final RotateWebhookSecretUseCase rotate = new RotateWebhookSecretUseCase(projectService, repository);

    @BeforeEach
    void 사용자() {
        UserContext.set(UserContext.builder().accountId("1").timezone("Asia/Seoul").build());
        when(projectService.validateOwnership(10L, 1L)).thenReturn(mock(Project.class));
        when(projectService.validateOwnershipForUpdate(10L, 1L)).thenReturn(mock(Project.class));
    }

    @AfterEach
    void 정리() {
        UserContext.clear();
    }

    private static WebhookConfig config(Long id, String secret) {
        return WebhookConfig.builder().id(id).project(Project.builder().id(10L).build()).webhookUrl("https://8.8.8.8/h")
                .demoEnabled(false).apiEnabled(true).webhookSecret(secret).build();
    }

    @Test
    @DisplayName("키가 없던 설정은 조회할 때 조건부로 채우고, 다시 읽은 값(먼저 들어간 키)을 보여 준다")
    void 조회_채움() {
        when(repository.findByProjectId(10L))
                .thenReturn(Optional.of(config(5L, null)), Optional.of(config(5L, "whsec_winner")));

        WebhookConfigResult result = get.execute(10L);

        verify(repository).assignSecretIfAbsent(eq(5L), anyString());
        assertThat(result.webhookSecret()).as("내가 만든 키가 아니라 저장된 키").isEqualTo("whsec_winner");
    }

    @Test
    @DisplayName("키가 있으면 조회는 쓰지 않는다")
    void 조회_그대로() {
        when(repository.findByProjectId(10L)).thenReturn(Optional.of(config(5L, "whsec_a")));

        assertThat(get.execute(10L).webhookSecret()).isEqualTo("whsec_a");
        verify(repository, never()).assignSecretIfAbsent(anyLong(), anyString());
    }

    @Test
    @DisplayName("재발급: 새 키, 옛 키 만료는 약 24시간 뒤, 행을 잠그고 읽는다")
    void 재발급() {
        WebhookConfig c = config(5L, "whsec_old");
        when(repository.findForUpdateByProjectId(10L)).thenReturn(Optional.of(c));

        WebhookConfigResult result = rotate.execute(10L, false);

        assertThat(result.webhookSecret()).startsWith("whsec_").isNotEqualTo("whsec_old");
        assertThat(result.previousSecretExpiresAt())
                .isCloseTo(LocalDateTime.now(ZoneOffset.UTC).plusHours(24), org.assertj.core.api.Assertions.within(1, ChronoUnit.MINUTES));
        assertThat(c.getPreviousWebhookSecret()).isEqualTo("whsec_old");
        verify(repository, never()).findByProjectId(anyLong());
        verify(projectService).validateOwnershipForUpdate(10L, 1L);
    }

    @Test
    @DisplayName("설정이 없으면 PJ-110")
    void 재발급_설정_없음() {
        when(repository.findForUpdateByProjectId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rotate.execute(10L, false))
                .isInstanceOf(CustomGateException.class)
                .extracting(e -> ((CustomGateException) e).getErrorType())
                .isEqualTo(ErrorType.WEBHOOK_CONFIG_NOT_FOUND);
    }

    @Test
    @DisplayName("즉시 폐기를 고르면 옛 키도 만료 시각도 없다")
    void 재발급_즉시_폐기() {
        WebhookConfig c = config(5L, "whsec_old");
        when(repository.findForUpdateByProjectId(10L)).thenReturn(Optional.of(c));

        WebhookConfigResult result = rotate.execute(10L, true);

        assertThat(result.webhookSecret()).startsWith("whsec_").isNotEqualTo("whsec_old");
        assertThat(result.previousSecretExpiresAt()).isNull();
        assertThat(c.getPreviousWebhookSecret()).isNull();
    }
}
