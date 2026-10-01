package ai.univs.gate.modules.webhook.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.support.jpa.JpaSliceTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 서명 키 조건부 채우기와 잠금 조회가 실제 쿼리로 도는가 (UG-344). JPQL 오타·파생 쿼리 이름은 기동 시점에야
 * 드러나므로 여기서 본다.
 */
@JpaSliceTest
@Import(WebhookConfigRepositoryImpl.class)
@DisplayName("UG-344: 웹훅 서명 키 저장소")
class WebhookConfigSecretSliceTest {

    @Autowired private WebhookConfigRepositoryImpl repository;
    @Autowired private EntityManager em;

    private Project project;
    private WebhookConfig config;

    @BeforeEach
    void setUp() {
        project = Project.builder().accountId(9L).projectName("p").branchName("branch-ug344")
                .isDeleted(false).status(ProjectStatus.ACTIVE).build();
        em.persist(project);
        config = WebhookConfig.builder().project(project).webhookUrl("https://8.8.8.8/h")
                .demoEnabled(false).apiEnabled(true).build();
        em.persist(config);
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("비어 있을 때만 채운다 — 두 번째 호출은 0행이고 먼저 들어간 키가 남는다")
    void 조건부_채우기() {
        assertThat(repository.assignSecretIfAbsent(config.getId(), "whsec_first")).isTrue();
        assertThat(repository.assignSecretIfAbsent(config.getId(), "whsec_second")).isFalse();

        assertThat(repository.findByProjectId(project.getId()).orElseThrow().getWebhookSecret())
                .isEqualTo("whsec_first");
    }

    @Test
    @DisplayName("잠금 조회가 같은 설정을 돌려준다")
    void 잠금_조회() {
        assertThat(repository.findForUpdateByProjectId(project.getId()))
                .get().extracting(WebhookConfig::getId).isEqualTo(config.getId());
    }
}
