package ai.univs.gate.migration;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.support.jpa.JpaSliceTest;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * UG-344: V37 웹훅 설정 중복 정리 SQL 을 <b>파일에서 읽어 원문 그대로</b> 실행한다 (선례: {@code DuplicateApiKeyCleanupSqlTest}).
 *
 * <p>이 마이그레이션은 행을 지운다. SQL 을 테스트에 다시 적으면 파일이 바뀌어도 초록이므로 파일을 읽는다.
 *
 * <p><b>H2 스키마의 제약을 뗀다.</b> 엔티티의 {@code @OneToOne} 때문에 Hibernate 가 {@code project_id} 에 유니크와 FK 를
 * 만들어 중복을 넣을 수조차 없다 — 운영 DB 는 V38 전까지 유니크가 없고 FK 는 처음부터 없다(V1). H2 는 FK 가 그 유니크
 * 인덱스를 함께 쓰므로 FK 를 먼저 뗀다. 공유 슬라이스 DB 를 건드리지 않도록 이 클래스만
 * 별도 인메모리 DB 를 쓴다.
 */
@JpaSliceTest
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:gate-webhook-dedupe;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@DisplayName("UG-344: 웹훅 설정 중복 정리 SQL")
class WebhookConfigDuplicateCleanupSqlTest {

    private static final Path MIGRATION = Path.of("src/main/resources/db/migration");
    private static final String CLEANUP = "V37__delete_duplicate_webhook_configs.sql";
    private static final String UNIQUE = "V38__unique_project_id_on_webhook_configs.sql";
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 1, 0, 0);

    @Autowired private EntityManager em;

    @BeforeEach
    void 운영과_같이_제약을_뗀다() {
        @SuppressWarnings("unchecked")
        List<String> names = em.createNativeQuery("""
                SELECT constraint_name FROM information_schema.table_constraints
                 WHERE LOWER(table_name) = 'webhook_configs' AND constraint_type IN ('FOREIGN KEY', 'UNIQUE')
                 ORDER BY CASE constraint_type WHEN 'FOREIGN KEY' THEN 0 ELSE 1 END
                """).getResultList();
        // H2 의 DDL 은 자동 커밋이라 한 번 떼면 같은 DB 의 다음 테스트에는 없다. V38 테스트가 다시 건 제약도 여기서 뗀다.
        for (String name : names) {
            em.createNativeQuery("ALTER TABLE webhook_configs DROP CONSTRAINT \"" + name + "\"").executeUpdate();
        }
    }

    @Test
    @DisplayName("프로젝트마다 가장 최근에 저장된 행 하나만 남는다 — 위반 없는 프로젝트는 그대로다")
    void 프로젝트마다_최근_하나() {
        Long 셋 = 프로젝트("p3");
        Long 둘 = 프로젝트("p2");
        Long 하나 = 프로젝트("p1");
        설정(셋, "https://8.8.8.8/a", T0, T0.plusHours(1));
        Long 셋의_최신 = 설정(셋, "https://8.8.8.8/b", T0, T0.plusHours(3));
        설정(셋, "https://8.8.8.8/c", T0, T0.plusHours(2));
        설정(둘, "https://8.8.8.8/d", T0, T0.plusHours(1));
        Long 둘의_최신 = 설정(둘, "https://8.8.8.8/e", T0, T0.plusHours(5));
        Long 하나의_유일 = 설정(하나, "https://8.8.8.8/f", T0, T0);

        assertThat(정리한다()).isEqualTo(3);

        assertThat(남은_설정(셋)).as("PARTITION BY 가 빠지면 전 프로젝트에서 하나만 남는다").containsExactly(셋의_최신);
        assertThat(남은_설정(둘)).containsExactly(둘의_최신);
        assertThat(남은_설정(하나)).containsExactly(하나의_유일);
    }

    @Test
    @DisplayName("updated_at 이 같으면 id 가 큰 행을 남긴다")
    void 동률() {
        Long p = 프로젝트("p-tie");
        설정(p, "https://8.8.8.8/a", T0, T0.plusHours(1));
        Long 나중_id = 설정(p, "https://8.8.8.8/b", T0, T0.plusHours(1));

        정리한다();

        assertThat(남은_설정(p)).containsExactly(나중_id);
    }

    @Test
    @DisplayName("id 가 작아도 나중에 저장된 행을 남긴다 — MAX(id) 기준이 아니다")
    void 최근_저장_기준() {
        Long p = 프로젝트("p-skew");
        Long 먼저_만들었지만_나중에_저장 = 설정(p, "https://8.8.8.8/a", T0, T0.plusHours(9));
        설정(p, "https://8.8.8.8/b", T0, T0.plusHours(1));

        정리한다();

        assertThat(남은_설정(p)).containsExactly(먼저_만들었지만_나중에_저장);
    }

    @Test
    @DisplayName("빈 테이블·위반 없는 환경에서는 아무것도 지우지 않는다")
    void 조용하다() {
        assertThat(정리한다()).isZero();
        설정(프로젝트("p-ok"), "https://8.8.8.8/a", T0, T0);
        assertThat(정리한다()).isZero();
    }

    @Test
    @DisplayName("정리 후 V38 유니크 제약이 걸리고, 그 뒤로 중복 INSERT 는 거부된다")
    void 정리_후_유니크() {
        Long p = 프로젝트("p-uq");
        설정(p, "https://8.8.8.8/a", T0, T0);
        설정(p, "https://8.8.8.8/b", T0, T0.plusHours(1));
        정리한다();

        em.createNativeQuery(SQL만_남긴다(읽는다("postgresql", UNIQUE))).executeUpdate();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            설정(p, "https://8.8.8.8/c", T0, T0);
            em.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("두 방언의 V37·V38 문장이 같다 — 오라클은 여기서 실행할 수 없어 이 대조가 방어선이다")
    void 두_방언이_같은_문장이다() {
        assertThat(SQL만_남긴다(읽는다("oracle", CLEANUP))).isEqualTo(SQL만_남긴다(읽는다("postgresql", CLEANUP)));
        assertThat(SQL만_남긴다(읽는다("oracle", UNIQUE))).isEqualTo(SQL만_남긴다(읽는다("postgresql", UNIQUE)));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Long 프로젝트(String name) {
        Project project = Project.builder().accountId(9L).projectName(name).branchName("branch-" + name)
                .isDeleted(false).status(ProjectStatus.ACTIVE).build();
        em.persist(project);
        em.flush();
        return project.getId();
    }

    /** 감사 컬럼을 직접 정하려고 네이티브로 넣는다. */
    private Long 설정(Long projectId, String url, LocalDateTime createdAt, LocalDateTime updatedAt) {
        em.createNativeQuery("""
                INSERT INTO webhook_configs (project_id, webhook_url, demo_enabled, api_enabled, created_at, created_by,
                                             updated_at, updated_by)
                VALUES (?, ?, FALSE, TRUE, ?, 0, ?, 0)
                """)
                .setParameter(1, projectId).setParameter(2, url)
                .setParameter(3, Timestamp.valueOf(createdAt))
                .setParameter(4, Timestamp.valueOf(updatedAt))
                .executeUpdate();
        return ((Number) em.createNativeQuery("SELECT MAX(webhook_config_id) FROM webhook_configs").getSingleResult())
                .longValue();
    }

    private int 정리한다() {
        em.flush();
        em.clear();
        return em.createNativeQuery(SQL만_남긴다(읽는다("postgresql", CLEANUP))).executeUpdate();
    }

    @SuppressWarnings("unchecked")
    private List<Long> 남은_설정(Long projectId) {
        return ((List<Number>) em.createNativeQuery(
                        "SELECT webhook_config_id FROM webhook_configs WHERE project_id = ? ORDER BY webhook_config_id")
                .setParameter(1, projectId).getResultList())
                .stream().map(Number::longValue).toList();
    }

    private static String 읽는다(String dialect, String file) {
        try {
            return Files.readString(MIGRATION.resolve(dialect).resolve(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("마이그레이션 파일을 못 읽었다: " + dialect + "/" + file, e);
        }
    }

    private static String SQL만_남긴다(String source) {
        return source.replaceAll("--[^\n]*", " ").replaceAll("\\s+", " ").trim().replaceAll(";$", "");
    }
}
