package ai.univs.match.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.match.application.input.RegisterWithFaceIdInput;
import ai.univs.match.application.service.BranchProvisioner;
import ai.univs.match.application.service.DuplicateService;
import ai.univs.match.application.service.IdentifyService;
import ai.univs.match.application.service.RecentDuplicateCheck;
import ai.univs.match.application.service.RegisterService;
import ai.univs.match.infrastructure.persistence.BranchCustomRepository;
import ai.univs.match.infrastructure.persistence.DescriptorCustomRepository;
import ai.univs.match.infrastructure.persistence.OracleBranchCustomRepositoryImpl;
import ai.univs.match.infrastructure.persistence.OracleDescriptorCustomRepositoryImpl;
import ai.univs.match.infrastructure.persistence.PostgresqlBranchCustomRepositoryImpl;
import ai.univs.match.infrastructure.persistence.PostgresqlDescriptorCustomRepositoryImpl;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.exception.TransactionManagerConfig;
import ai.univs.match.shared.web.enums.ErrorType;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.dao.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 얼굴 동시 등록 — <b>실제 PostgreSQL·Oracle</b> 에서 본다 (UG-374). 평소 빌드에서는 돌지 않는다.
 *
 * <p>CI 에는 DB 가 없고 이 환경은 이미지를 받지 못해 Testcontainers 를 쓸 수 없다. 로컬에 있는 이미지로 띄운 <b>버려도 되는</b>
 * DB 를 환경변수로 가리켜 돌린다. 테이블을 비우고 {@code vlmatch} 가 없으면 대역(같은 바이트면 거리 0, 아니면 10)을 만든다 —
 * Oracle 대역은 실제 함수처럼 BINARY_FLOAT 를 돌려준다(MatchOracleProjection 의 float) —
 * 운영 DB 를 가리키면 안 된다.
 * <pre>
 * UG374_DB=postgresql|oracle UG374_DB_URL=jdbc:... UG374_DB_USER=... UG374_DB_PASSWORD=... \
 *   sh gradlew test --tests '*SameFaceConcurrentRegisterRealDbTest'
 * </pre>
 *
 * <p>운영처럼 풀 10·대기 1.5초, 요청마다 OSIV 범위 EntityManager 를 둔다. Flyway 로 실제 마이그레이션(V6 인덱스 포함)을 건다.
 */
@EnabledIfEnvironmentVariable(named = "UG374_DB_URL", matches = ".+")
@DataJpaTest(properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.show-sql=false",
        "spring.flyway.enabled=true",
        "spring.flyway.clean-disabled=true",
        "spring.datasource.hikari.maximum-pool-size=10",
        "spring.datasource.hikari.connection-timeout=1500"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(PersistenceExceptionTranslationAutoConfiguration.class)
@Import({RegisterUseCase.class, RegisterWithFaceIdUseCase.class, RegisterService.class, BranchProvisioner.class,
        RecentDuplicateCheck.class, DuplicateService.class, IdentifyService.class, TransactionManagerConfig.class,
        SameFaceConcurrentRegisterRealDbTest.Dialect.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("UG-374: 같은 얼굴 동시 등록 — 실제 DB")
class SameFaceConcurrentRegisterRealDbTest {

    private static final String DB = System.getenv().getOrDefault("UG374_DB", "postgresql");
    private static final boolean ORACLE = DB.equals("oracle");
    private static final int REQUESTS = 16;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("UG374_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("UG374_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("UG374_DB_PASSWORD"));
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/" + DB);
    }

    /** 프로필 대신 환경변수로 방언 구현을 고른다 — RepositoryConfig 와 같은 구현이다 */
    @TestConfiguration(proxyBeanMethods = false)
    static class Dialect {
        @Bean
        DescriptorCustomRepository descriptorCustomRepository(EntityManager em) {
            return ORACLE ? new OracleDescriptorCustomRepositoryImpl(em) : new PostgresqlDescriptorCustomRepositoryImpl(em);
        }

        @Bean
        BranchCustomRepository branchCustomRepository(EntityManager em) {
            return ORACLE ? new OracleBranchCustomRepositoryImpl(em) : new PostgresqlBranchCustomRepositoryImpl(em);
        }
    }

    @Autowired private RegisterUseCase registerUseCase;
    @Autowired private RegisterWithFaceIdUseCase registerWithFaceIdUseCase;
    @Autowired private EntityManagerFactory emf;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DescriptorCustomRepository descriptorCustomRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private ListAppender<ILoggingEvent> sqlErrors;

    @BeforeEach
    void setUp() {
        vlmatch_대역();
        jdbc.update("DELETE FROM descriptor");
        jdbc.update("DELETE FROM branch");
        sqlErrors = new ListAppender<>();
        sqlErrors.start();
        ((Logger) LoggerFactory.getLogger("org.hibernate.engine.jdbc.spi.SqlExceptionHelper")).addAppender(sqlErrors);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger("org.hibernate.engine.jdbc.spi.SqlExceptionHelper")).detachAppender(sqlErrors);
    }

    private void vlmatch_대역() {
        if (ORACLE) {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM user_objects WHERE object_name = 'VLMATCH'", Integer.class);
            if (n != null && n == 0) {
                jdbc.execute("""
                        CREATE FUNCTION vlmatch(a RAW, b RAW, v NUMBER) RETURN BINARY_FLOAT DETERMINISTIC IS
                        BEGIN
                            IF a = b THEN RETURN 0; ELSE RETURN 10; END IF;
                        END;""");
            }
        } else {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM pg_proc WHERE proname = 'vlmatch'", Integer.class);
            if (n != null && n == 0) {
                jdbc.execute("""
                        CREATE FUNCTION vlmatch(a bytea, b bytea, v integer) RETURNS double precision
                        AS $$ SELECT CASE WHEN a = b THEN 0.0 ELSE 10.0 END $$ LANGUAGE sql IMMUTABLE""");
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    /** 얼굴마다 다른 특징점 — 본문 첫 바이트로 구분한다(대역 vlmatch 는 바이트가 같아야 거리 0). */
    private static String 얼굴(int person) {
        byte[] bytes = new byte[520];
        bytes[4] = (byte) 59;
        bytes[8] = (byte) person;
        bytes[9] = (byte) (person >> 8);
        return Base64.getEncoder().encodeToString(bytes);
    }

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

    private record 결과(int 성공, int 이미_등록, List<String> 그_외) {
    }

    private 결과 동시에(String branch, List<Supplier<String>> descriptors, boolean withFaceId) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(descriptors.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < descriptors.size(); i++) {
                Supplier<String> descriptor = descriptors.get(i);
                String faceId = java.util.UUID.randomUUID().toString();
                futures.add(pool.submit(() -> {
                    start.await();
                    return OSIV_안에서(() -> {
                        try {
                            if (withFaceId) {
                                registerWithFaceIdUseCase.execute(new RegisterWithFaceIdInput(branch, faceId, descriptor.get()));
                            } else {
                                registerUseCase.execute(branch, descriptor.get());
                            }
                            return "OK";
                        } catch (CustomFaceMatcherException e) {
                            return e.getErrorType().name();
                        }
                    });
                }));
            }
            start.countDown();
            int ok = 0;
            int dup = 0;
            List<String> other = new ArrayList<>();
            for (Future<String> f : futures) {
                String r;
                try {
                    r = f.get(60, TimeUnit.SECONDS);
                } catch (Exception e) {
                    r = String.valueOf(e.getCause() != null ? e.getCause() : e);
                }
                if (r.equals("OK")) {
                    ok++;
                } else if (r.equals(ErrorType.ALREADY_REGISTERED_DESCRIPTOR.name())) {
                    dup++;
                } else {
                    other.add(r);
                }
            }
            return new 결과(ok, dup, other);
        } finally {
            pool.shutdownNow();
        }
    }

    private long 특징점_수(String branch) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM descriptor d JOIN branch b ON b.branch_id = d.branch_id "
                + "WHERE b.branch_name = ?", Long.class, branch);
    }

    private long 브랜치_수(String branch) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM branch WHERE branch_name = ?", Long.class, branch);
    }

    private long 브랜치_위반_ERROR_줄() {
        return sqlErrors.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .filter(e -> e.getFormattedMessage().toLowerCase().contains("uk_branch_branch_name"))
                .count();
    }

    private static List<Supplier<String>> 같은_얼굴(int n) {
        List<Supplier<String>> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(() -> 얼굴(7));
        }
        return list;
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("새 프로젝트(브랜치 없음)에 같은 얼굴 16건 동시 — 정확히 1건 성공, 15건 MATCH-003, 브랜치 1행, 특징점 1행")
    void 새_브랜치_같은_얼굴() throws Exception {
        String branch = "ug374-new-" + System.nanoTime();

        결과 r = 동시에(branch, 같은_얼굴(REQUESTS), false);

        assertThat(r.그_외()).as("성공·MATCH-003 이외").isEmpty();
        assertThat(r.성공()).isEqualTo(1);
        assertThat(r.이미_등록()).isEqualTo(REQUESTS - 1);
        assertThat(브랜치_수(branch)).isEqualTo(1);
        assertThat(특징점_수(branch)).isEqualTo(1);
        if (!ORACLE) {
            assertThat(브랜치_위반_ERROR_줄()).as("PostgreSQL ON CONFLICT — 위반 ERROR 줄 0").isZero();
        }
        System.out.printf("[UG-374 %s] 새 브랜치 같은 얼굴: 성공 %d / MATCH-003 %d, 브랜치 위반 ERROR 줄 %d%n",
                DB, r.성공(), r.이미_등록(), 브랜치_위반_ERROR_줄());
    }

    @Test
    @DisplayName("이미 있는 브랜치(다른 얼굴 1건)에 같은 얼굴 16건 동시(faceId 지정) — 정확히 1건 성공")
    void 기존_브랜치_같은_얼굴() throws Exception {
        String branch = "ug374-old-" + System.nanoTime();
        OSIV_안에서(() -> registerUseCase.execute(branch, 얼굴(1)));

        결과 r = 동시에(branch, 같은_얼굴(REQUESTS), true);

        assertThat(r.그_외()).isEmpty();
        assertThat(r.성공()).isEqualTo(1);
        assertThat(r.이미_등록()).isEqualTo(REQUESTS - 1);
        assertThat(특징점_수(branch)).isEqualTo(2);
    }

    @Test
    @DisplayName("서로 다른 얼굴 16건 동시 — 모두 성공(재검사가 다른 사람을 물리지 않는다, 잠금이 교착하지 않는다)")
    void 다른_얼굴은_모두_성공() throws Exception {
        String branch = "ug374-diff-" + System.nanoTime();
        OSIV_안에서(() -> registerUseCase.execute(branch, 얼굴(1)));
        List<Supplier<String>> faces = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            int person = 100 + i;
            faces.add(() -> 얼굴(person));
        }

        long started = System.nanoTime();
        결과 r = 동시에(branch, faces, false);
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(r.그_외()).isEmpty();
        assertThat(r.성공()).isEqualTo(REQUESTS);
        assertThat(특징점_수(branch)).isEqualTo(REQUESTS + 1);
        System.out.printf("[UG-374 %s] 다른 얼굴 %d건 동시: %d ms%n", DB, REQUESTS, millis);
    }

    @Test
    @DisplayName("같은 얼굴을 차례로 두 번 — 두 번째는 사전 중복 검사로 MATCH-003 (예전과 같다)")
    void 차례로는_사전_검사() {
        String branch = "ug374-seq-" + System.nanoTime();
        OSIV_안에서(() -> registerUseCase.execute(branch, 얼굴(5)));

        String second = OSIV_안에서(() -> {
            try {
                registerUseCase.execute(branch, 얼굴(5));
                return "OK";
            } catch (CustomFaceMatcherException e) {
                return e.getErrorType().name();
            }
        });

        assertThat(second).isEqualTo(ErrorType.ALREADY_REGISTERED_DESCRIPTOR.name());
        assertThat(특징점_수(branch)).isEqualTo(1);
    }

    /**
     * 재검사 잠금이 특징점 INSERT 를 막지 않는가 — 막으면 같은 브랜치의 등록이 모두 재검사 뒤에 줄을 선다. 지금은 외래키가
     * 없어 잠금 종류와 상관없이 통과한다. 잠금 종류(PostgreSQL FOR NO KEY UPDATE)는 {@code RecheckQueryDialectTest} 가 묶는다 —
     * 외래키가 생기면 이 테스트가 그 선택이 맞는지 실제로 보여 준다.
     */
    @Test
    @DisplayName("재검사 잠금을 쥔 동안에도 같은 브랜치에 특징점 INSERT 는 기다리지 않는다")
    void 잠금이_INSERT_를_막지_않는다() throws Exception {
        String branch = "ug374-lock-" + System.nanoTime();
        OSIV_안에서(() -> registerUseCase.execute(branch, 얼굴(1)));
        Long branchId = jdbc.queryForObject("SELECT branch_id FROM branch WHERE branch_name = ?", Long.class, branch);
        String url = System.getenv("UG374_DB_URL");
        String user = System.getenv("UG374_DB_USER");
        String password = System.getenv("UG374_DB_PASSWORD");

        // 잠금은 실제 저장소 메서드로 건다 — 잠금 종류(FOR NO KEY UPDATE / FOR UPDATE)를 바꾸는 변경을 잡으려고
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch inserted = new CountDownLatch(1);
        ExecutorService lockHolder = Executors.newSingleThreadExecutor();
        Future<?> holding = lockHolder.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            descriptorCustomRepository.lockBranchForRecheck(branchId);
            locked.countDown();
            try {
                inserted.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            status.setRollbackOnly();
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).as("잠금을 쥐었다").isTrue();

        try (Connection writer = DriverManager.getConnection(url, user, password)) {
            writer.setAutoCommit(false);
            try (Statement s = writer.createStatement()) {
                if (!ORACLE) {
                    s.execute("SET lock_timeout = '2s'");
                }
            }
            String insert = "INSERT INTO descriptor (branch_id, face_id, " + (ORACLE ? "\"DESCRIPTOR\"" : "descriptor")
                    + ", descriptor_type, descriptor_body, descriptor_obtaining_method, descriptor_version, "
                    + "descriptor_generation, created_at, modified_at) VALUES (?, ?, ?, ?, ?, 0, 59, 0, ?, ?)";
            byte[] legacy = Base64.getDecoder().decode(얼굴(2));
            Timestamp now = Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC));
            long started = System.nanoTime();
            try (PreparedStatement ps = writer.prepareStatement(insert)) {
                if (ORACLE) {
                    ps.setQueryTimeout(3);
                }
                ps.setLong(1, branchId);
                ps.setString(2, java.util.UUID.randomUUID().toString());
                ps.setBytes(3, legacy);
                ps.setBytes(4, java.util.Arrays.copyOfRange(legacy, 0, 8));
                ps.setBytes(5, java.util.Arrays.copyOfRange(legacy, 8, legacy.length));
                ps.setTimestamp(6, now);
                ps.setTimestamp(7, now);
                ps.executeUpdate();
            }
            long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            writer.rollback();
            assertThat(millis).as("INSERT 가 잠금을 기다렸다").isLessThan(1000);
        } finally {
            inserted.countDown();
            holding.get(10, TimeUnit.SECONDS);
            lockHolder.shutdownNow();
        }
    }

    /**
     * PostgreSQL 일반 실행 계획(generic plan)에서도 재검사 쿼리가 인덱스로 최근 행만 읽는가. 큰 갤러리에서 일반 계획이 브랜치
     * 전체를 훑던 문제(scaling 측정)가 이 쿼리에 옮겨 오지 않는지 본다. 행을 20만 개 넣고 본다.
     */
    @Test
    @DisplayName("PostgreSQL: 일반 실행 계획에서도 재검사 쿼리는 (branch_id, created_at) 인덱스를 쓴다")
    void 일반_계획에서도_인덱스() {
        if (ORACLE) {
            return;
        }
        jdbc.execute("INSERT INTO branch (branch_name, created_at, modified_at) "
                + "SELECT 'ug374-bulk-' || g, now(), now() FROM generate_series(1, 20) g");
        jdbc.execute("""
                INSERT INTO descriptor (branch_id, face_id, descriptor, descriptor_type, descriptor_body,
                    descriptor_obtaining_method, descriptor_version, descriptor_generation, created_at, modified_at)
                SELECT b.branch_id, md5(random()::text || g), decode(repeat('00', 520), 'hex'), decode(repeat('00', 8), 'hex'),
                       decode(repeat('00', 512), 'hex'), 0, 59, 0,
                       now() - (g || ' seconds')::interval, now()
                FROM generate_series(1, 10000) g CROSS JOIN branch b WHERE b.branch_name LIKE 'ug374-bulk-%'""");
        jdbc.execute("ANALYZE descriptor");
        Long bulkBranch = jdbc.queryForObject(
                "SELECT min(branch_id) FROM branch WHERE branch_name LIKE 'ug374-bulk-%'", Long.class);

        List<String> plan = jdbc.execute((java.sql.Connection c) -> {
            List<String> lines = new ArrayList<>();
            try (Statement s = c.createStatement()) {
                s.execute("SET plan_cache_mode = force_generic_plan");
                s.execute("""
                        PREPARE ug374 (bigint, timestamp, bigint, integer, bytea, integer) AS
                        SELECT d.face_id, CAST(vlmatch($5, d.descriptor_body, $6) AS double precision) AS distance
                        FROM descriptor d
                        WHERE d.branch_id = $1 AND d.created_at >= $2 AND d.descriptor_id <> $3 AND d.descriptor_version = $4
                        ORDER BY distance ASC, d.face_id ASC LIMIT 1""");
                try (var rs = s.executeQuery("EXPLAIN EXECUTE ug374 (" + bulkBranch
                        + ", now() - interval '60 seconds', 0, 59, decode(repeat('00', 512), 'hex'), 512)")) {
                    while (rs.next()) {
                        lines.add(rs.getString(1));
                    }
                }
                s.execute("DEALLOCATE ug374");
                s.execute("RESET plan_cache_mode");
            }
            return lines;
        });

        String joined = String.join("\n", plan);
        System.out.println("[UG-374 postgresql] generic plan:\n" + joined);
        assertThat(joined).contains("idx_descriptor_branch_created");
        assertThat(joined).doesNotContain("Seq Scan on descriptor");
    }
}
