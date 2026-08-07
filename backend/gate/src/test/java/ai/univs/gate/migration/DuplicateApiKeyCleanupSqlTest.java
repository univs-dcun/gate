package ai.univs.gate.migration;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.support.jpa.JpaSliceTest;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * UG-302: V23 정리 SQL 을 <b>실제 데이터베이스에서 원문 그대로</b> 실행해 확인한다.
 *
 * <p>이 마이그레이션은 <b>데이터를 지운다</b> — 활성 API 키를 비활성화한다. 그런데 반박 리뷰가
 * 변이 15종을 심었더니 <b>전부 살아남았다.</b> {@code GROUP BY} 를 지워 전 프로젝트의 키를
 * 하나만 남기게 만드는 변이조차 초록이었다. SQL 의 의미를 보는 테스트가 한 줄도 없었기
 * 때문이다. 대조군과 진짜 변이가 구분되지 않는다 = 커버리지 0.
 *
 * <p>그래서 파일에서 SQL 을 <b>읽어서</b> 실행한다. 여기에 SQL 을 다시 적으면 파일이 바뀌어도
 * 테스트는 그대로 통과한다 — 이 세션에서 여러 번 반복된 동어반복이다.
 *
 * <p><b>postgresql 쪽만 실행한다.</b> 이 슬라이스는 H2 를 PostgreSQL 모드로 띄우고 스키마를
 * 엔티티에서 만들므로 {@code is_active} 가 BOOLEAN 이다. 오라클 파일은 {@code NUMBER(1,0)} 을
 * 전제해 {@code = 1} 로 비교하니 그대로는 돌지 않는다. 대신 두 파일이 <b>같은 문장</b>임을
 * 문자 단위로 대조한다 ({@link #두_방언이_같은_문장이다()}) — 한쪽만 고치는 실수가 이 프로젝트의
 * 실제 사고 유형이다 (UG-292, UG-297).
 */
@JpaSliceTest
@DisplayName("UG-302: 중복 API 키 정리 SQL")
class DuplicateApiKeyCleanupSqlTest {

    private static final Path MIGRATION = Path.of("src/main/resources/db/migration");
    private static final String CLEANUP = "V23__deactivate_duplicate_api_keys.sql";

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("프로젝트마다 하나씩만 남는다 — 전역에서 하나가 아니다")
    void 프로젝트마다_하나씩_남는다() {
        Project 셋 = 프로젝트("p-three");
        Project 둘 = 프로젝트("p-two");
        Project 하나 = 프로젝트("p-one");
        Project 없음 = 프로젝트("p-none");

        키(셋, "k31", true, 1);
        키(셋, "k32", true, 2);
        Long 셋의_최신 = 키(셋, "k33", true, 3);
        키(셋, "k34", false, 0);
        키(둘, "k21", true, 1);
        Long 둘의_최신 = 키(둘, "k22", true, 2);
        Long 하나의_유일 = 키(하나, "k11", true, 1);
        키(없음, "k01", false, 1);

        정리_SQL_을_실행한다();

        assertThat(활성_키(셋))
                .as("GROUP BY/PARTITION BY 가 빠지면 전 프로젝트에서 딱 하나만 남는다 — "
                        + "나머지 고객의 연동이 전부 끊긴다")
                .containsExactly(셋의_최신);
        assertThat(활성_키(둘)).containsExactly(둘의_최신);
        assertThat(활성_키(하나))
                .as("위반이 아닌 프로젝트는 건드리면 안 된다")
                .containsExactly(하나의_유일);
        assertThat(활성_키(없음)).isEmpty();
    }

    /**
     * 남기는 행이 조회 경로와 같아야 한다.
     *
     * <p>{@code findLatestActiveByProjectId} 는 {@code issued_at DESC, id DESC} 다. 초판
     * 마이그레이션은 {@code MAX(api_key_id)} 를 썼는데, 반박 리뷰가 두 기준이 갈리는 반례를
     * 실측했다 — {@code issued_at} 은 DB 기본값이 아니라 애플리케이션이 넣으므로,
     * {@code now()} 계산과 INSERT 사이에 다른 스레드가 끼어들면 <b>issued_at 이 더 큰데 id 가
     * 더 작은 행</b>이 생긴다. 활성 2개 상태 자체가 그 동시 호출로 생긴 것이라 남 얘기가 아니다.
     *
     * <p>그 경우 정리가 "화면이 보여 주던 키" 를 꺼 버린다. 데이터를 지우는 결정의 유일한
     * 근거가 무너지는 것이라 여기서 못박는다.
     */
    @Test
    @DisplayName("issued_at 이 큰데 id 가 작은 행을 남긴다 — 조회 경로와 같은 기준")
    void 조회_경로와_같은_행을_남긴다() {
        Project p = 프로젝트("p-skew");
        Long 늦게_발급됐지만_먼저_저장된_키 = 키(p, "k-late-issued", true, 10);
        Long 먼저_발급됐지만_나중에_저장된_키 = 키(p, "k-early-issued", true, 5);

        정리_SQL_을_실행한다();

        assertThat(활성_키(p))
                .as("MAX(api_key_id) 기준이면 id 가 큰 %s 가 남아 화면과 어긋난다",
                        먼저_발급됐지만_나중에_저장된_키)
                .containsExactly(늦게_발급됐지만_먼저_저장된_키);
    }

    @Test
    @DisplayName("위반이 없으면 아무 행도 바꾸지 않는다")
    void 위반이_없으면_조용하다() {
        Project p = 프로젝트("p-clean");
        Long 유일 = 키(p, "k-only", true, 1);
        키(p, "k-old", false, 0);

        assertThat(정리_SQL_을_실행한다())
                .as("멀쩡한 환경에서 행을 건드리면 안 된다")
                .isZero();
        assertThat(활성_키(p)).containsExactly(유일);
    }

    @Test
    @DisplayName("빈 테이블에서도 안전하다 — 신규 설치에서 V1~V24 가 연달아 돈다")
    void 빈_테이블에서도_안전하다() {
        assertThat(정리_SQL_을_실행한다()).isZero();
    }

    /**
     * 두 방언 파일이 같은 문장인지.
     *
     * <p>한쪽만 고치는 것이 이 프로젝트의 실제 사고 유형이다 (UG-292 절 순서, UG-297 기본값
     * 불일치). {@code DialectSchemaParityTest} 는 컬럼 선언만 보므로 UPDATE 문은 대상 밖이다.
     *
     * <p>불리언 표기만 다르다 — 오라클 SQL 에는 불리언 리터럴이 없어 {@code NUMBER(1,0)} 에
     * 0/1 을 쓴다. 그 치환을 되돌리면 두 파일의 SQL 은 완전히 같아야 한다.
     */
    @Test
    @DisplayName("두 방언의 정리 문장이 불리언 표기만 빼고 동일하다")
    void 두_방언이_같은_문장이다() {
        String pg = SQL만_남긴다(읽는다("postgresql"));
        String oracle = SQL만_남긴다(읽는다("oracle"))
                .replace("set is_active = 0", "set is_active = false")
                .replace("where is_active = 1", "where is_active = true");

        assertThat(oracle)
                .as("한쪽만 고치면 두 환경의 데이터가 달라진다. 오라클 쪽은 실행해 볼 수단이 "
                        + "없으므로(H2 스키마가 PostgreSQL 모드다) 이 대조가 유일한 방어선이다")
                .isEqualTo(pg);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** 파일에서 읽어 실행한다. 여기에 SQL 을 다시 적으면 파일이 바뀌어도 통과한다. */
    private int 정리_SQL_을_실행한다() {
        em.flush();
        em.clear();
        int affected = em.createNativeQuery(SQL만_남긴다(읽는다("postgresql"))).executeUpdate();
        em.clear();
        return affected;
    }

    private static String 읽는다(String dialect) {
        try {
            return Files.readString(MIGRATION.resolve(dialect).resolve(CLEANUP),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("마이그레이션 파일을 못 읽었다: " + dialect, e);
        }
    }

    /** 주석을 걷어내고 공백·대소문자를 정규화한다. 세미콜론도 뗀다 (JPA 네이티브 쿼리). */
    private static String SQL만_남긴다(String source) {
        return source.replaceAll("--[^\n]*", " ")
                .replaceAll("\\s+", " ")
                .replace(";", "")
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
    }

    private Project 프로젝트(String branch) {
        Project p = Project.builder()
                .accountId(100L)
                .projectName("테스트")
                .branchName(branch)
                .isDeleted(false)
                .status(ProjectStatus.ACTIVE)
                .build();
        em.persist(p);
        return p;
    }

    /** {@code 발급시각오프셋} 이 클수록 최근에 발급된 것으로 본다. */
    private Long 키(Project owner, String key, boolean active, int 발급시각오프셋) {
        ApiKey apiKey = ApiKey.builder()
                .project(owner)
                .apiKey(key)
                .secretKey("secret-" + key)
                .issuedAt(LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(발급시각오프셋))
                .isActive(active)
                .build();
        em.persist(apiKey);
        return apiKey.getId();
    }

    @SuppressWarnings("unchecked")
    private List<Long> 활성_키(Project owner) {
        return em.createQuery(
                        "select k.id from ApiKey k "
                                + "where k.project = :p and k.isActive = true order by k.id",
                        Long.class)
                .setParameter("p", em.find(Project.class, owner.getId()))
                .getResultList();
    }

    /** 사용하지 않지만 의도를 남긴다: 오라클 쪽은 실행 수단이 없다. */
    private static final Map<String, String> 실행하지_않는_이유 =
            Map.of("oracle", "H2 슬라이스가 PostgreSQL 모드라 is_active 가 BOOLEAN 이다. "
                    + "오라클 파일은 NUMBER(1,0) 을 전제해 = 1 로 비교한다.");
}
