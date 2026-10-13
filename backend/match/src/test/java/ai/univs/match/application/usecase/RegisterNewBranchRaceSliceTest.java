package ai.univs.match.application.usecase;

import ai.univs.match.application.input.RegisterWithFaceIdInput;
import ai.univs.match.application.result.MatchResult;
import ai.univs.match.application.service.BranchProvisioner;
import ai.univs.match.application.service.DuplicateService;
import ai.univs.match.application.service.RecentDuplicateCheck;
import ai.univs.match.infrastructure.persistence.BranchCustomRepository;
import ai.univs.match.application.service.RegisterService;
import ai.univs.match.shared.exception.TransactionManagerConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.dao.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 새 브랜치의 첫 등록이 동시에 와도 모두 성공하는가 — 실제 트랜잭션·커넥션 풀로 본다 (UG-372).
 *
 * <p>Testcontainers 를 못 쓰므로(이미지 pull 불가) H2(PostgreSQL 모드)를 쓴다. H2 는 문장이 실패해도 트랜잭션을 계속 쓸 수
 * 있어 PostgreSQL 과 다르다 — 그대로면 "같은 트랜잭션에서 위반을 잡고 다시 읽기" 도 통과한다. 그래서
 * {@link PostgresAbortEmulatingDataSource} 로 PostgreSQL 의 중단 규칙(이후 문장 25P02, 커밋은 조용히 롤백)을 씌우고,
 * 매 테스트에서 그런 일이 <b>한 번도 없었음</b>을 확인한다.
 *
 * <p>풀은 <b>2</b> 다. 요청 하나가 커넥션을 둘 쥐는 구조(바깥 트랜잭션 + REQUIRES_NEW)면 아래 동시 등록에서 두 요청이 커넥션을
 * 하나씩 쥔 채 서로를 기다리다 대기 상한에 걸린다 — 운영 풀(10, 1.5초)에서 첫 등록이 10건 넘게 몰릴 때와 같은 모양이다.
 *
 * <p>제약은 Flyway 대신 여기서 같은 이름으로 건다(V4·V5). 이름이 마이그레이션과 같은지는 {@code UniqueConstraintNameGuardTest}.
 * 중복 판정(1:N, PostgreSQL 전용 vlmatch)과 커밋 뒤 재검사(UG-374, {@code FOR NO KEY UPDATE} 는 H2 에 없다)는 목으로 둔다 —
 * 여기서 보는 것은 브랜치 생성뿐이다.
 *
 * <p>브랜치 생성(UG-374)의 방언 SQL 은 H2 가 모른다 — PostgreSQL {@code ON CONFLICT} 는 문법 오류, Oracle 힌트는 주석으로
 * 무시돼 그냥 INSERT 가 된다. 그래서 같은 계약("있으면 0 건, 위반 없음")을 H2 표준 {@code MERGE} 로 흉내 낸 대역을 쓴다.
 * 표준 MERGE 는 동시에 돌면 위반이 나므로 "위반을 잡아 다시 읽기" 경로도 PostgreSQL 중단 규칙 아래에서 함께 본다. 방언
 * SQL 의 실제 동작은 {@code SameFaceConcurrentRegisterRealDbTest}(PostgreSQL 17·Oracle 23).
 */
@DataJpaTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.datasource.url=" + RegisterNewBranchRaceSliceTest.URL,
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=0",
        "spring.datasource.hikari.connection-timeout=5000"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// 운영처럼 @Repository 예외 번역을 켠다 — @DataJpaTest 는 이 자동 설정을 올리지 않는다. 브랜치 생성의 위반 처리가 기대는 것이다 (UG-374)
@ImportAutoConfiguration(PersistenceExceptionTranslationAutoConfiguration.class)
@Import({RegisterUseCase.class, RegisterWithFaceIdUseCase.class, RegisterService.class, BranchProvisioner.class,
        TransactionManagerConfig.class, RegisterNewBranchRaceSliceTest.AbortEmulation.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("UG-372: 새 브랜치 첫 등록 경합")
class RegisterNewBranchRaceSliceTest {

    static final String URL = "jdbc:h2:mem:match-ug372;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String BRANCH = "branch-ug372";

    @TestConfiguration(proxyBeanMethods = false)
    static class AbortEmulation {
        /** H2 대역 — 계약은 운영 구현과 같다(있으면 0 건). RepositoryConfig 는 슬라이스에 올라오지 않는다 */
        @Bean
        BranchCustomRepository branchCustomRepository(EntityManager em) {
            return (branchName, now) -> em.createNativeQuery("""
                        MERGE INTO branch b
                        USING dual
                        ON (b.branch_name = :branchName)
                        WHEN NOT MATCHED THEN
                            INSERT (branch_name, created_at, modified_at) VALUES (:branchName, :now, :now)
                    """)
                    .setParameter("branchName", branchName)
                    .setParameter("now", now)
                    .executeUpdate();
        }

        @Bean
        static BeanPostProcessor postgresAbortEmulation() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    return bean instanceof DataSource ds && !(bean instanceof PostgresAbortEmulatingDataSource)
                            ? new PostgresAbortEmulatingDataSource(ds) : bean;
                }
            };
        }
    }

    @MockBean private DuplicateService duplicateService;
    /** 기본값(false) — 재검사 통과. 같은 얼굴 경합은 {@code SameFaceConcurrentRegisterRealDbTest} 가 실제 DB 로 본다 */
    @MockBean private RecentDuplicateCheck recentDuplicateCheck;

    @Autowired private RegisterUseCase registerUseCase;
    @Autowired private RegisterWithFaceIdUseCase registerWithFaceIdUseCase;
    @Autowired private DataSource dataSource;
    @Autowired private EntityManagerFactory emf;
    @Autowired private PlatformTransactionManager transactionManager;

    private PostgresAbortEmulatingDataSource db;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        db = (PostgresAbortEmulatingDataSource) dataSource;
        jdbc = new JdbcTemplate(dataSource);
        // ddl-auto 는 byte[] 를 VARBINARY(255) 로 만든다. 특징점은 520 바이트(운영은 BYTEA·BLOB).
        jdbc.execute("ALTER TABLE descriptor ALTER COLUMN descriptor SET DATA TYPE VARBINARY(1024)");
        jdbc.execute("ALTER TABLE descriptor ALTER COLUMN descriptor_body SET DATA TYPE VARBINARY(1024)");
        jdbc.execute("ALTER TABLE branch ADD CONSTRAINT IF NOT EXISTS uk_branch_branch_name UNIQUE (branch_name)");
        jdbc.execute("ALTER TABLE descriptor ADD CONSTRAINT IF NOT EXISTS uk_descriptor_branch_face UNIQUE (branch_id, face_id)");
        jdbc.update("DELETE FROM descriptor");
        jdbc.update("DELETE FROM branch");
        db.reset();
    }

    @AfterEach
    void tearDown() {
        db.reset();
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static String descriptor() {
        byte[] bytes = new byte[520];
        bytes[4] = (byte) 59;
        return Base64.getEncoder().encodeToString(bytes);
    }

    private long 브랜치_행수() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM branch WHERE branch_name = ?", Long.class, BRANCH);
    }

    private Long 브랜치_id() {
        return jdbc.queryForObject("SELECT branch_id FROM branch WHERE branch_name = ?", Long.class, BRANCH);
    }

    private List<Long> 특징점_브랜치_id들() {
        return jdbc.queryForList("SELECT branch_id FROM descriptor", Long.class);
    }

    /** 풀 밖의 별도 커넥션으로 같은 이름의 브랜치를 만들고 커밋한다 — "다른 요청이 먼저 이겼다". */
    private static void 다른_요청이_먼저_커밋() {
        try (Connection other = DriverManager.getConnection(URL, "sa", "");
             PreparedStatement ps = other.prepareStatement(
                     "INSERT INTO branch (branch_name, created_at, modified_at) VALUES (?, ?, ?)")) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            ps.setString(1, BRANCH);
            ps.setTimestamp(2, now);
            ps.setTimestamp(3, now);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** OpenEntityManagerInView 처럼 요청 범위의 EntityManager 를 묶어 둔다 — 운영 match 는 OSIV 기본값(켜짐)이다. */
    private <T> T OSIV_안에서(Supplier<T> body) {
        EntityManager em = emf.createEntityManager();
        TransactionSynchronizationManager.bindResource(emf, new EntityManagerHolder(em));
        try {
            return body.get();
        } finally {
            TransactionSynchronizationManager.unbindResource(emf);
            EntityManagerFactoryUtils.closeEntityManager(em);
        }
    }

    /** 동시 등록을 두 유스케이스 모두로 돌린다 — 어느 쪽에 {@code @Transactional} 이 되살아나도 잡히게. */
    enum 등록_경로 {
        일반_등록, faceId_지정_등록
    }

    private MatchResult 등록(등록_경로 경로, String faceId) {
        return switch (경로) {
            case 일반_등록 -> registerUseCase.execute(BRANCH, descriptor());
            case faceId_지정_등록 -> registerWithFaceIdUseCase.execute(new RegisterWithFaceIdInput(BRANCH, faceId, descriptor()));
        };
    }

    private void 중단된_트랜잭션은_없었다() {
        assertThat(db.rejectedInAbortedTransaction).as("중단된 트랜잭션에서 실행한 문장(PostgreSQL 이면 25P02)").hasValue(0);
        assertThat(db.silentRollbacks).as("중단된 트랜잭션을 커밋(PostgreSQL 이면 조용히 롤백)").hasValue(0);
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("전제: 데이터소스는 크기 2 인 Hikari 풀을 감싼 PostgreSQL 중단 흉내다")
    void 전제() {
        assertThat(dataSource).isInstanceOf(PostgresAbortEmulatingDataSource.class);
        assertThat(db.getTargetDataSource()).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource) db.getTargetDataSource()).getMaximumPoolSize()).isEqualTo(2);
    }

    /** 흉내가 실제로 걸리는가 — 아니면 아래 「중단된 트랜잭션은 없었다」 가 아무것도 증명하지 못한다. */
    @Test
    @DisplayName("전제: 오류 난 트랜잭션의 다음 문장은 25P02, 커밋은 조용히 롤백된다")
    void 전제_중단_흉내() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement insert = c.prepareStatement(
                    "INSERT INTO branch (branch_name, created_at, modified_at) VALUES ('ug372-probe', NULL, NULL)")) {
                insert.executeUpdate();
                // 같은 이름 한 번 더 — 실행 시점에 유니크 위반 (H2 는 없는 테이블 같은 오류를 prepare 에서 낸다)
                assertThatThrownBy(insert::executeUpdate).isInstanceOf(java.sql.SQLException.class);
            }
            try (PreparedStatement next = c.prepareStatement("SELECT 1")) {
                assertThatThrownBy(next::executeQuery).hasFieldOrPropertyWithValue("SQLState", "25P02");
            }
            c.commit();
            c.setAutoCommit(true);
        }
        assertThat(db.rejectedInAbortedTransaction).hasValue(1);
        assertThat(db.silentRollbacks).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM branch WHERE branch_name = 'ug372-probe'", Long.class))
                .as("커밋했지만 버려졌다").isZero();
    }

    @Test
    @DisplayName("(1) 경합 없는 새 브랜치 첫 등록 — 브랜치 한 행, 특징점이 그 브랜치에 저장된다")
    void 새_브랜치_첫_등록() {
        MatchResult result = registerUseCase.execute(BRANCH, descriptor());

        assertThat(result.branchName()).isEqualTo(BRANCH);
        assertThat(브랜치_행수()).isEqualTo(1);
        assertThat(특징점_브랜치_id들()).containsExactly(브랜치_id());
        assertThat(db.branchNameViolations).hasValue(0);
        중단된_트랜잭션은_없었다();
    }

    @Test
    @DisplayName("이미 있는 브랜치 — 브랜치를 더 만들지 않고 특징점만 늘어난다")
    void 있는_브랜치() {
        registerWithFaceIdUseCase.execute(new RegisterWithFaceIdInput(BRANCH, "face-1", descriptor()));
        registerWithFaceIdUseCase.execute(new RegisterWithFaceIdInput(BRANCH, "face-2", descriptor()));

        assertThat(브랜치_행수()).isEqualTo(1);
        assertThat(특징점_브랜치_id들()).containsExactly(브랜치_id(), 브랜치_id());
        중단된_트랜잭션은_없었다();
    }

    /**
     * 「없다」 를 본 뒤 INSERT 직전에 다른 요청이 같은 이름을 커밋한다. UG-340 에서는 여기서 INTERNAL_SERVER_ERROR 였다.
     * UG-372 에서는 위반을 받고 다시 읽었다(ERROR 로그 한 줄). UG-374 부터는 INSERT 가 위반 없이 0 건으로 끝나고 다시 읽는다.
     */
    @Test
    @DisplayName("(2) 경합 주입 — 진 쪽도 성공하고, 브랜치는 이긴 쪽 한 행, 특징점은 그 브랜치에 저장된다")
    void 경합에서_진_쪽도_성공() {
        db.beforeBranchInsert.set(RegisterNewBranchRaceSliceTest::다른_요청이_먼저_커밋);

        MatchResult result = registerWithFaceIdUseCase.execute(new RegisterWithFaceIdInput(BRANCH, "face-race", descriptor()));

        assertThat(result.faceId()).isEqualTo("face-race");
        assertThat(db.branchInsertsSkipped).as("경합이 실제로 났는가 — INSERT 가 0 건").hasValue(1);
        assertThat(db.branchNameViolations).as("위반(= Hibernate ERROR 줄)은 없다").hasValue(0);
        assertThat(브랜치_행수()).isEqualTo(1);
        assertThat(특징점_브랜치_id들()).containsExactly(브랜치_id());
        중단된_트랜잭션은_없었다();
    }

    /** 운영은 OSIV 라 생성 실패와 다시 읽기, 등록이 한 EntityManager 를 이어 쓴다. 실패한 생성이 남긴 것이 없어야 한다. */
    @Test
    @DisplayName("(2') 경합 주입 — 요청 범위 EntityManager(OSIV) 아래에서도 같다")
    void 경합에서_진_쪽도_성공_OSIV() {
        db.beforeBranchInsert.set(RegisterNewBranchRaceSliceTest::다른_요청이_먼저_커밋);

        MatchResult result = OSIV_안에서(() ->
                registerWithFaceIdUseCase.execute(new RegisterWithFaceIdInput(BRANCH, "face-race", descriptor())));

        assertThat(result.faceId()).isEqualTo("face-race");
        assertThat(db.branchInsertsSkipped).hasValue(1);
        assertThat(db.branchNameViolations).hasValue(0);
        assertThat(브랜치_행수()).isEqualTo(1);
        assertThat(특징점_브랜치_id들()).containsExactly(브랜치_id());
        중단된_트랜잭션은_없었다();
    }

    /**
     * 실제 동시 등록. 처음 두 요청은 브랜치 조회 직후 서로를 기다리게 해 <b>둘 다 「없다」 를 보고</b> INSERT 하게 만든다 — 경합이
     * 반드시 한 번 이상 난다. 풀이 2 라 요청 하나가 커넥션을 둘 쥐는 구조였다면 두 요청이 서로를 기다리다 대기 상한에 걸린다.
     * 두 유스케이스({@link RegisterUseCase}·{@link RegisterWithFaceIdUseCase})를 각각 돌린다 — 한쪽에만 {@code @Transactional}
     * 이 되살아나고 가드까지 빠지면 그쪽 경우만 깨진다.
     */
    @ParameterizedTest(name = "(3) {0} — 새 브랜치에 16건 동시 등록(풀 2), 모두 성공, 브랜치 한 행, 특징점 16행")
    @EnumSource(등록_경로.class)
    void 동시_등록(등록_경로 경로) throws Exception {
        int requests = 16;
        CyclicBarrier firstTwo = new CyclicBarrier(2);
        AtomicInteger barrierSlots = new AtomicInteger(2);
        db.afterBranchSelect = () -> {
            if (barrierSlots.getAndDecrement() > 0) {
                try {
                    firstTwo.await(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException("두 요청이 함께 조회하지 못했다", e);
                }
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MatchResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                String faceId = "face-" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    return 등록(경로, faceId);
                }));
            }
            start.countDown();
            List<String> failures = new ArrayList<>();
            for (Future<MatchResult> f : futures) {
                try {
                    f.get(60, TimeUnit.SECONDS);
                } catch (Exception e) {
                    failures.add(String.valueOf(e.getCause() != null ? e.getCause() : e));
                }
            }
            assertThat(failures).as("실패한 등록").isEmpty();
        } finally {
            pool.shutdownNow();
            db.afterBranchSelect = () -> { };
        }

        assertThat(db.branchInsertsSkipped.get() + db.branchNameViolations.get())
                .as("경합이 실제로 났는가").isGreaterThanOrEqualTo(1);
        assertThat(브랜치_행수()).isEqualTo(1);
        Long branchId = 브랜치_id();
        assertThat(특징점_브랜치_id들()).hasSize(requests).containsOnly(branchId);
        중단된_트랜잭션은_없었다();
    }

    /** 유스케이스에 {@code @Transactional} 을 되살리는 변경을 막는다 — 실제 트랜잭션 매니저 아래에서 거부되는지. */
    @Test
    @DisplayName("트랜잭션 안에서 등록하면 브랜치를 만들기 전에 거부한다 (풀 교착 구조 방지)")
    void 트랜잭션_안에서는_거부() {
        TransactionTemplate outer = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> outer.executeWithoutResult(s -> registerUseCase.execute(BRANCH, descriptor())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UG-372");
        assertThat(브랜치_행수()).isZero();
    }
}
