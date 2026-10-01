package ai.univs.gate.modules.webhook.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.infrastructure.persistence.BiometricFeatureJpaRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.support.jpa.JpaSliceTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * 잠금 조회가 <b>PostgreSQL 방언에서</b> 단일 {@code SELECT ... FOR UPDATE} 로 렌더링되는가 (UG-344 반박 리뷰 B1).
 *
 * <p>PostgreSQL 방언은 외부 조인에 FOR UPDATE 를 걸지 못한다({@code supportsOuterJoinForUpdate() == false}). 조회가
 * 연관을 LEFT JOIN 으로 렌더링하면 Hibernate 는 "잠금 없이 읽고 → id 로 따로 잠그는" follow-on locking 으로 바꾸고,
 * 잠근 뒤 다시 읽지 않는다. 그러면 잠금이 있어도 낡은 값을 되써 동시 갱신을 지운다. 기본 슬라이스(H2 방언)에서는
 * 단일 문장으로 렌더링돼 이 문제가 보이지 않으므로, 방언만 바꿔 SQL 을 직접 본다.
 *
 * <p>UG-345 의 특징점 삭제 잠금 조회도 같은 함정에 걸리면 동시 삭제의 웹훅이 두 번 나간다 — 함께 지킨다.
 */
@JpaSliceTest
@TestPropertySource(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "ai.univs.gate.modules.webhook.infrastructure.persistence.RecordingStatementInspector"
})
@DisplayName("UG-344: 잠금 조회의 PostgreSQL 렌더링")
class WebhookConfigLockSqlSliceTest {

    @Autowired private WebhookConfigJpaRepository webhookConfigs;
    @Autowired private BiometricFeatureJpaRepository features;
    @Autowired private EntityManager em;

    private Project project;
    private BiometricFeature feature;

    @BeforeEach
    void setUp() {
        project = Project.builder().accountId(9L).projectName("p").branchName("branch-ug344-lock")
                .isDeleted(false).status(ProjectStatus.ACTIVE).build();
        em.persist(project);
        em.persist(WebhookConfig.builder().project(project).webhookUrl("https://8.8.8.8/h")
                .demoEnabled(false).apiEnabled(true).build());
        feature = BiometricFeature.builder().project(project).type(FeatureType.FACE).featureId("fid-lock")
                .isDeleted(false).build();
        em.persist(feature);
        em.flush();
        em.clear();
        RecordingStatementInspector.SQL.clear();
    }

    @Test
    @DisplayName("웹훅 설정 잠금 조회는 조인 없는 한 문장이고, 그 문장이 잠근다 (follow-on locking 아님)")
    void 웹훅_설정() {
        assertThat(webhookConfigs.findForUpdateByProjectId(project.getId())).isPresent();

        assertSingleLockingSelect();
    }

    @Test
    @DisplayName("UG-345: 특징점 삭제의 잠금 재조회도 같은 조건을 지킨다")
    void 특징점() {
        assertThat(features.findForUpdateByIdAndTypeAndIsDeletedFalse(feature.getId(), FeatureType.FACE)).isPresent();

        assertSingleLockingSelect();
    }

    private static void assertSingleLockingSelect() {
        List<String> sql = RecordingStatementInspector.SQL.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
        assertThat(sql).as("잠금 없이 읽는 문장과 잠그는 문장으로 나뉘면 follow-on locking 이다").hasSize(1);
        // 조인을 아예 금지한다 — 내부 조인은 PostgreSQL 에서도 한 문장(FOR NO KEY UPDATE OF ...)으로 되지만, 외부 조인으로
        // 바뀌는 순간 follow-on locking 이 된다. 잠금 조회에는 조인이 필요 없으므로 엄격하게 둔다.
        assertThat(sql.getFirst()).contains(" for ").doesNotContain(" join ");
    }
}
