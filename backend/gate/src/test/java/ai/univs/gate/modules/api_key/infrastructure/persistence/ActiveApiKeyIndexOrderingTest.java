package ai.univs.gate.modules.api_key.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.support.jpa.JpaSliceTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

/**
 * UG-302: V24 인덱스 아래에서 <b>재발급의 쓰기 순서</b>가 성립하는지.
 *
 * <p>반박 리뷰가 이 방식으로 BLOCKER 를 찾았다. V24 가 만드는 부분 유니크 인덱스는 "프로젝트당
 * 활성 키 하나" 를 DB 가 강제한다. 그런데 재발급은 "기존 비활성화 → 새 키 삽입" 이고,
 * {@code deactivate()} 는 더티 마킹일 뿐이다. 그 사이의 {@code ApiKeyGenerator} 는
 * {@code SecureRandom} 만 써서 DB 를 건드리지 않으므로 auto-flush 도 일어나지 않는다.
 *
 * <p>{@code @GeneratedValue(IDENTITY)} 라 {@code persist()} 가 id 를 받으려고 INSERT 를 즉시
 * 내보내는데, 그 시점에 DB 에는 기존 행이 <b>아직 활성</b>이다 → 인덱스 위반. 즉 인덱스를
 * 넣는 순간 <b>모든 프로젝트의 키 재발급이 실패</b>한다. UG-302 가 고치려던 증상을 특정
 * 프로젝트가 아니라 전체로 확대하는 셈이고, 자가 치유 수단인 재발급이 죽으므로 복구 경로까지
 * 함께 사라진다.
 *
 * <p><b>지연 제약으로는 못 피한다.</b> {@code DEFERRABLE INITIALLY DEFERRED} 는 제약에만 붙는데
 * 부분 인덱스는 제약으로 선언할 수 없다. 오라클 함수 기반 유니크 인덱스도 같다. 애플리케이션
 * 쪽에서 순서를 고쳐야 한다.
 *
 * <p><b>인덱스를 흉내낸다.</b> 이 슬라이스의 스키마는 마이그레이션이 아니라 엔티티에서
 * 만들어지므로 V24 의 인덱스가 없다. H2 는 부분 인덱스를 지원하지 않아 생성 컬럼 + 유니크
 * 인덱스로 같은 성질을 만든다 — 활성이 아닌 행은 컬럼이 NULL 이 되고, 단일 컬럼 유니크
 * 인덱스는 NULL 행끼리 충돌시키지 않는다. postgresql 부분 인덱스·오라클 함수 기반 인덱스와
 * 같은 동작이다.
 *
 * <p>DDL 은 H2 에서 암묵적 커밋이라 이 클래스의 트랜잭션 격리가 깨진다. 다른 슬라이스
 * 테스트로 새어 나가지 않도록 {@code @DirtiesContext} 로 컨텍스트를 버리고, 검사도 메서드
 * 하나에 몰아 뒀다.
 */
@JpaSliceTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("UG-302: V24 인덱스 아래의 재발급 쓰기 순서")
class ActiveApiKeyIndexOrderingTest {

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("비활성화를 flush 해야 새 키를 넣을 수 있다 — flush 가 빠지면 재발급이 전부 실패한다")
    void flush_없이는_인덱스에_걸린다() {
        인덱스를_흉내낸다();

        Project project = 프로젝트();
        키(project, "gate_old_key_for_ordering_probe", true);
        em.flush();
        em.clear();

        Project 재조회 = em.find(Project.class, project.getId());
        ApiKey 기존 = 활성_키(재조회);

        // ── flush 없이: deactivate 는 더티 마킹뿐이라 INSERT 가 먼저 나간다
        기존.deactivate();
        assertThatThrownBy(() -> {
            em.persist(새_키(재조회, "gate_new_key_without_flush_xx"));
            em.flush();
        })
                .as("이 예외가 안 나면 흉내낸 인덱스가 제 역할을 못 하는 것이다 — "
                        + "그러면 아래 통과도 의미가 없다")
                .isInstanceOf(Exception.class);

        em.clear();

        // ── flush 를 끼우면: UPDATE 가 먼저 나가고 INSERT 가 통과한다
        Project 다시 = em.find(Project.class, project.getId());
        ApiKey 기존2 = 활성_키(다시);
        기존2.deactivate();
        em.flush();

        assertThatCode(() -> {
            em.persist(새_키(다시, "gate_new_key_with_flush_xxxx"));
            em.flush();
        })
                .as("RegenerateApiKeyUseCase 가 deactivate 뒤에 apiKeyRepository.flush() 를 "
                        + "부르는 이유가 이것이다")
                .doesNotThrowAnyException();

        em.clear();
        assertThat(em.createQuery(
                        "select count(k) from ApiKey k where k.project.id = :p and k.isActive = true",
                        Long.class)
                .setParameter("p", project.getId())
                .getSingleResult())
                .as("정상 종료 후에는 활성 키가 정확히 하나여야 한다")
                .isEqualTo(1L);
    }

    /**
     * 부분 유니크 인덱스를 H2 에서 흉내낸다.
     *
     * <p>H2 는 {@code CREATE UNIQUE INDEX ... WHERE} 를 지원하지 않는다(실측: Syntax error).
     * 활성일 때만 값을 갖는 생성 컬럼에 유니크 인덱스를 걸면 같은 성질이 된다.
     */
    private void 인덱스를_흉내낸다() {
        em.createNativeQuery(
                        "ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS active_project BIGINT "
                                + "GENERATED ALWAYS AS (CASE WHEN is_active THEN project_id END)")
                .executeUpdate();
        em.createNativeQuery(
                        "CREATE UNIQUE INDEX IF NOT EXISTS ux_probe_active_project "
                                + "ON api_keys (active_project)")
                .executeUpdate();
    }

    /**
     * 인덱스가 <b>부분</b>이어야 하는 이유를 동작으로 보인다.
     *
     * <p>{@code WHERE is_active} 를 빼면 {@code project_id} 전체 유니크가 되어 프로젝트당 키
     * 행이 <b>영원히 하나</b>다 — 비활성 키가 이력으로 쌓이지 못하므로 재발급이 두 번째부터
     * 불가능해진다. 변이 심기에서 그 형태가 살아남았다(반박 리뷰 M5).
     *
     * <p><b>텍스트 검사인 것을 감춰 두지 않는다.</b> 전체 유니크를 H2 로 흉내내 동작까지 보이려
     * 했는데, 그 DDL 이 암묵 커밋이라 같은 클래스의 순서 검사를 오염시킨다(전체 유니크가 걸리면
     * 그쪽이 키를 두 개 못 넣는다). 별도 컨텍스트를 하나 더 띄우는 값보다 텍스트 대조가 싸고,
     * 막으려는 것(파일이 전체 유니크로 바뀌는 것)에는 충분하다.
     */
    @Test
    @DisplayName("전체 유니크였다면 비활성 이력조차 못 쌓는다 — 그래서 부분 인덱스여야 한다")
    void 부분_인덱스여야_한다() throws java.io.IOException {
        java.nio.file.Path v24 =
                java.nio.file.Path.of("src/main/resources/db/migration");
        String pg = java.nio.file.Files.readString(
                v24.resolve("postgresql/V24__unique_active_api_key_per_project.sql"));
        String oracle = java.nio.file.Files.readString(
                v24.resolve("oracle/V24__unique_active_api_key_per_project.sql"));

        assertThat(pg.replaceAll("--[^\n]*", " "))
                .as("WHERE 술어가 없으면 project_id 전체 유니크가 되어 프로젝트당 키 행이 "
                        + "영원히 하나다 — 비활성 이력이 못 쌓여 재발급이 두 번째부터 막힌다")
                .containsIgnoringCase("where is_active");
        assertThat(oracle.replaceAll("--[^\n]*", " "))
                .as("오라클은 부분 인덱스 문법이 없어 CASE 로 같은 것을 표현한다. CASE 가 빠지면 "
                        + "비활성 키까지 인덱스에 실려 postgresql 과 다른 제약이 된다")
                .containsIgnoringCase("case when is_active");

        // 두 방언의 인덱스 이름이 다르면 운영에서 한쪽만 존재하는 것을 알아채기 어렵다.
        assertThat(색인_이름(oracle)).isEqualTo(색인_이름(pg));

    }

    private static String 색인_이름(String sql) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("CREATE\\s+UNIQUE\\s+INDEX\\s+(\\w+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(sql);
        assertThat(m.find()).as("CREATE UNIQUE INDEX 를 못 찾았다").isTrue();
        return m.group(1).toLowerCase(java.util.Locale.ROOT);
    }

    private Project 프로젝트() {
        Project p = Project.builder()
                .accountId(100L)
                .projectName("순서 프로브")
                .branchName("ordering-probe")
                .isDeleted(false)
                .status(ProjectStatus.ACTIVE)
                .build();
        em.persist(p);
        return p;
    }

    private ApiKey 키(Project owner, String key, boolean active) {
        ApiKey apiKey = 새_키(owner, key);
        apiKey.setIsActive(active);
        em.persist(apiKey);
        return apiKey;
    }

    private static ApiKey 새_키(Project owner, String key) {
        return ApiKey.builder()
                .project(owner)
                .apiKey(key)
                .secretKey("secret-" + key)
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC))
                .isActive(true)
                .build();
    }

    private ApiKey 활성_키(Project owner) {
        return em.createQuery(
                        "select k from ApiKey k where k.project = :p and k.isActive = true",
                        ApiKey.class)
                .setParameter("p", owner)
                .getSingleResult();
    }
}
