package ai.univs.match.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * UG-374 의 방언별 SQL 모양 — 커밋 뒤 재검사(잠금·최근 비교)와 위반 없는 브랜치 생성.
 *
 * <p>{@link TopKQueryDialectTest} 와 같은 방식이다. 이 SQL 들은 모양이 틀리면 예외 없이 조용히 틀린다 — 잠금 종류를 잘못
 * 고르면 같은 브랜치의 등록이 모두 줄을 서고, 조건이 하나 빠지면 자기 자신과 비교해 모든 등록이 물러난다. 실제 DB 동작은
 * {@code SameFaceConcurrentRegisterRealDbTest}.
 */
@DisplayName("UG-374: 재검사·브랜치 생성 쿼리의 방언별 형태")
class RecheckQueryDialectTest {

    private static final Long BRANCH_ID = 7L;
    private static final Long SELF_ID = 42L;
    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 10, 12, 0, 0);

    private EntityManager em;
    private Query query;

    @BeforeEach
    void 픽스처() {
        em = mock(EntityManager.class);
        query = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(em.createNativeQuery(anyString(), any(Class.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
    }

    /** 잠금에 쓴 네이티브 SQL 전부 (PostgreSQL 은 대기 상한 SET LOCAL + 잠금 SELECT) */
    private List<String> 잠금_SQL(DescriptorCustomRepository repository) {
        repository.lockBranchForRecheck(BRANCH_ID);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(em, atLeastOnce()).createNativeQuery(sql.capture());
        verify(query).setParameter("branchId", BRANCH_ID);
        return sql.getAllValues();
    }

    private String 최근_비교_SQL(DescriptorCustomRepository repository) {
        repository.nearestRecent(BRANCH_ID, SELF_ID, SINCE, 59, new byte[512], 512);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(em).createNativeQuery(sql.capture(), any(Class.class));
        verify(query).setParameter("branchId", BRANCH_ID);
        verify(query).setParameter("excludeDescriptorId", SELF_ID);
        verify(query).setParameter("since", SINCE);
        verify(query).setParameter("descriptorVersion", 59);
        return sql.getValue();
    }

    private static Pattern 패턴(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    @Test
    @DisplayName("postgresql 잠금은 FOR NO KEY UPDATE — 외래키가 생기면 FOR UPDATE 는 특징점 INSERT(부모 행 KEY SHARE)를 줄 세운다")
    void postgresql_잠금_종류() {
        List<String> sqls = 잠금_SQL(new PostgresqlDescriptorCustomRepositoryImpl(em));

        assertThat(sqls).hasSize(2);
        assertThat(sqls.get(0)).as("잠금보다 먼저, 이 트랜잭션에만 대기 상한 1초 (반박 리뷰 M2)")
                .containsPattern(패턴("SET\\s+LOCAL\\s+lock_timeout\\s*=\\s*'1000ms'"));
        assertThat(sqls.get(1)).containsPattern(패턴("FROM\\s+branch\\s+WHERE\\s+branch_id\\s*=\\s*:branchId"));
        assertThat(sqls.get(1)).containsPattern(패턴("FOR\\s+NO\\s+KEY\\s+UPDATE"));
        verify(query).executeUpdate();
    }

    @Test
    @DisplayName("oracle 잠금은 FOR UPDATE WAIT 1 — 자식 INSERT 가 부모 행을 잠그지 않는다, 대기 상한 1초")
    void oracle_잠금_종류() {
        List<String> sqls = 잠금_SQL(new OracleDescriptorCustomRepositoryImpl(em));

        assertThat(sqls).hasSize(1);
        assertThat(sqls.get(0)).containsPattern(패턴("FROM\\s+branch\\s+WHERE\\s+branch_id\\s*=\\s*:branchId"));
        assertThat(sqls.get(0)).containsPattern(패턴("FOR\\s+UPDATE\\s+WAIT\\s+1\\b"));
    }

    @Test
    @DisplayName("두 방언 모두 최근 비교에 브랜치·최근·자기 제외·같은 버전 조건을 건다")
    void 최근_비교_조건() {
        for (Function<EntityManager, DescriptorCustomRepository> 방언 : List.<Function<EntityManager, DescriptorCustomRepository>>of(
                PostgresqlDescriptorCustomRepositoryImpl::new, OracleDescriptorCustomRepositoryImpl::new)) {
            픽스처();
            String sql = 최근_비교_SQL(방언.apply(em));

            assertThat(sql).as("브랜치 — 빠지면 다른 테넌트의 얼굴과 비교한다")
                    .containsPattern(패턴("branch_id\\s*=\\s*:branchId"));
            assertThat(sql).as("최근 — 빠지면 브랜치 전체를 잠근 채 훑는다")
                    .containsPattern(패턴("created_at\\s*>=\\s*:since"));
            assertThat(sql).as("자기 제외 — 빠지면 자기와 비교해(거리 0) 모든 등록이 물러난다")
                    .containsPattern(패턴("descriptor_id\\s*<>\\s*:excludeDescriptorId"));
            assertThat(sql).as("같은 버전")
                    .containsPattern(패턴("descriptor_version\\s*=\\s*:descriptorVersion"));
            assertThat(sql).as("가장 가까운 것부터")
                    .containsPattern(패턴("ORDER\\s+BY\\s+distance\\s+ASC"));
        }
    }

    @Test
    @DisplayName("oracle 최근 비교는 ROWNUM 을 정렬된 인라인 뷰 바깥에서 자르고, parallel 힌트를 쓰지 않는다")
    void oracle_최근_비교_모양() {
        String sql = 최근_비교_SQL(new OracleDescriptorCustomRepositoryImpl(em));

        assertThat(sql.toUpperCase().indexOf("ROWNUM")).isGreaterThan(sql.lastIndexOf(')'));
        assertThat(sql).doesNotContainIgnoringCase("parallel");
    }

    @Test
    @DisplayName("postgresql 브랜치 생성은 ON CONFLICT (branch_name) DO NOTHING — 위반 ERROR 줄을 남기지 않는다")
    void postgresql_브랜치_생성() {
        new PostgresqlBranchCustomRepositoryImpl(em).insertIfAbsent("b", SINCE);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(em).createNativeQuery(sql.capture());
        assertThat(sql.getValue()).containsPattern(패턴("ON\\s+CONFLICT\\s*\\(\\s*branch_name\\s*\\)\\s+DO\\s+NOTHING"));
        verify(query).setParameter("branchName", "b");
        verify(query).executeUpdate();
    }

    @Test
    @DisplayName("oracle 브랜치 생성은 IGNORE_ROW_ON_DUPKEY_INDEX(branch(branch_name)) 힌트를 단 INSERT — MERGE 는 동시에 위반이 났다")
    void oracle_브랜치_생성() {
        new OracleBranchCustomRepositoryImpl(em).insertIfAbsent("b", SINCE);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(em).createNativeQuery(sql.capture());
        assertThat(sql.getValue())
                .as("힌트 문법이 틀리면 Oracle 은 조용히 무시하고 위반을 낸다 — 주석이 /*+ 로 시작해야 힌트다")
                .containsPattern(패턴("INSERT\\s*/\\*\\+\\s*IGNORE_ROW_ON_DUPKEY_INDEX\\s*\\(\\s*branch\\s*\\(\\s*branch_name\\s*\\)\\s*\\)\\s*\\*/\\s*INTO\\s+branch"));
        assertThat(sql.getValue()).doesNotContainIgnoringCase("MERGE");
        verify(query).setParameter("branchName", "b");
        verify(query).executeUpdate();
    }
}
