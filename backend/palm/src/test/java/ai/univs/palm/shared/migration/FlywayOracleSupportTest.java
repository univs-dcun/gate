package ai.univs.palm.shared.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import org.flywaydb.core.extensibility.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;

/**
 * UG-296: 오라클 마이그레이션이 실행될 수 있는 상태인지 지킨다.
 *
 * <p>Flyway 10 부터 DB 지원이 모듈로 분리됐다. {@code flyway-core} 하나만으로는 오라클을 모른다.
 * 이 레포는 {@code flyway-database-postgresql} 만 선언한 채로 {@code db/migration/oracle} 에
 * SQL 을 쌓아 왔고, 그래서 <b>그 SQL 은 한 번도 실행된 적이 없다</b> — 오라클 URL 로 기동하면
 * Flyway 가 URL 을 처리할 DatabaseType 을 못 찾아 부팅 시점에 죽는다. SQL 을 한 줄도 파싱하기
 * 전이다. UG-292 가 고친 절 순서 오류가 아무 신호도 내지 않은 진짜 이유다.
 *
 * <p>지키는 축이 둘이다.
 *
 * <ol>
 *   <li><b>플러그인 등록</b> — Flyway 가 실제로 쓰는 발견 경로
 *       ({@code META-INF/services/org.flywaydb.core.extensibility.Plugin})를
 *       {@code ServiceLoader} 로 그대로 조회한다. 클래스 존재만 보면 SPI 등록이 빠져도
 *       통과하고, {@code DatabaseTypeRegister} 는 내부 API 라 10.x 와 11.x 의 시그니처가 다르다.
 *   <li><b>마이그레이션 위치</b> — 프로파일 yml 을 실제로 <b>바인딩</b>해서 확인한다.
 * </ol>
 *
 * <p>두 번째 축은 처음에 yml 원문에 문자열이 들어 있는지만 봤는데, 반박 리뷰가 그게 껍데기임을
 * 실측으로 보여 줬다 — 현실적인 되돌림 8가지 중 6가지가 통과했다. 주석 처리, 오타
 * ({@code location:}), 값 뒤에 접미사 붙이기, {@code enabled: false} 추가, 다른 방언 폴더로
 * 바꾸기, {@code on-profile} 이름 바꾸기가 전부 초록이었다. 그래서 {@code Binder} 로 실제
 * 바인딩 결과를 본다. 스프링 컨텍스트는 띄우지 않는다.
 *
 * <p><b>주의: 이 파일이 최종 승자가 아닐 수 있다.</b> 이 레포의 규칙상 Spring 설정의 단일
 * 진실은 {@code univs-dcun/gate-config} 레포이고, 거기에도 같은 키를 가진
 * {@code application-oracle.yml} 이 있다. 두 값은 지금 같지만, config-server 쪽을 고치면
 * 이 테스트는 아무것도 잡지 못한다. 이 가드가 덮는 범위는 <b>레포 안의 파일까지</b>다.
 *
 * <p>SQL 이 실제 오라클에서 끝까지 도는지도 여전히 볼 수 없다 (UG-296 의 남은 항목).
 */
@DisplayName("UG-296: Flyway 오라클 지원 모듈")
class FlywayOracleSupportTest {

    private static final String ORACLE = "org.flywaydb.database.oracle.OracleDatabaseType";
    private static final String POSTGRESQL = "org.flywaydb.database.postgresql.PostgreSQLDatabaseType";

    private static final String ORACLE_ACCOUNT = "univs_palm";

    @Test
    @DisplayName("오라클 DatabaseType 이 Flyway 플러그인으로 등록돼 있다")
    void 오라클_DatabaseType_이_등록돼_있다() {
        Set<String> plugins = ServiceLoader.load(Plugin.class).stream()
                .map(provider -> provider.type().getName())
                .collect(Collectors.toSet());

        assertThat(plugins)
                .as("flyway-database-oracle 의존성이 빠지면 오라클 환경은 부팅조차 못 한다 (UG-296)")
                .contains(ORACLE);

        assertThat(plugins)
                .as("대조군 — 이것까지 없으면 SPI 조회 자체가 잘못된 것이다")
                .contains(POSTGRESQL);
    }

    /**
     * yml 을 실제로 바인딩해 {@code spring.flyway} 설정을 확인한다.
     *
     * <p>포스트그레스도 함께 본다. 같은 키가 같은 방식으로 깨질 수 있고, 그쪽은 지금 전 환경이
     * 실제로 쓰는 경로다 (리뷰 지적).
     *
     * <p>위치가 어긋나면 Flyway 는 기본값 {@code classpath:db/migration} 으로 되돌아간다.
     * <b>그 폴더는 존재하고</b> 하위에 {@code oracle/} 과 {@code postgresql/} 이 함께 있다 —
     * Flyway 스캐너는 재귀라 같은 버전 번호가 두 벌 잡히고 기동이 실패한다. 값에 접미사가
     * 붙거나 {@code enabled: false} 가 들어간 경우는 반대로 <b>아무것도 실행하지 않고 조용히
     * 성공한다.</b> 두 쪽 다 여기서 막는다.
     */
    @ParameterizedTest
    @CsvSource({
            "oracle,     application-oracle.yml,     classpath:db/migration/oracle",
            "postgresql, application-postgresql.yml, classpath:db/migration/postgresql",
    })
    @DisplayName("프로파일 yml 이 자기 방언 폴더를 가리킨다")
    void 프로파일_yml_이_자기_방언_폴더를_가리킨다(String profile, String file, String expected)
            throws IOException {
        Binder binder = binderFor(file);

        assertThat(binder.bind("spring.config.activate.on-profile", String.class).orElse(null))
                .as("%s 의 프로파일 이름이 바뀌면 이 문서가 통째로 활성화되지 않는다", file)
                .isEqualTo(profile);

        assertThat(binder.bind("spring.flyway.locations", String[].class).orElse(new String[0]))
                .as("이 값이 어긋나면 Flyway 가 기본 위치로 되돌아간다 — 값에 따라 중복 버전으로 "
                        + "기동 실패하거나, 아무것도 실행하지 않고 조용히 성공한다")
                .containsExactly(expected);

        assertThat(binder.bind("spring.flyway.enabled", Boolean.class).orElse(true))
                .as("enabled: false 가 들어가면 마이그레이션이 통째로 사라진다. 부팅은 되므로 "
                        + "아무도 알아채지 못한다")
                .isTrue();
    }

    private static Binder binderFor(String file) throws IOException {
        ClassPathResource resource = new ClassPathResource(file);
        assertThat(resource.exists())
                .as("%s 이 사라지면 그 프로파일 자체가 동작하지 않는다", file)
                .isTrue();

        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load(file, resource);
        assertThat(sources)
                .as("%s 이 비어 있다", file)
                .isNotEmpty();

        // 로더가 돌려준 PropertySource 를 그대로 쓴다. MapPropertySource 로 다시 감싸면
        // 값이 OriginTrackedValue 인 채로 남아 Boolean 변환이 실패한다.
        MutablePropertySources merged = new MutablePropertySources();
        // 여러 문서(---)가 있으면 앞 문서가 우선하도록 순서대로 넣는다.
        sources.forEach(merged::addLast);
        return new Binder(ConfigurationPropertySources.from(merged));
    }

    /** 식별자 한 조각. 따옴표로 감싼 것도 포함한다 — {@code "DESCRIPTOR"}. */
    private static final String 이름 = "[A-Za-z0-9_$\"]+";

    /** 점으로 이어 붙인 이름. 오라클은 점 좌우 공백을 허용한다 — {@code UNIVS . DESCRIPTOR}. */
    private static final String 한정된_이름 = "(" + 이름 + "(?:\\s*\\.\\s*" + 이름 + ")+)";

    /**
     * 객체 이름이 올 자리 뒤에 점이 들어간 이름.
     *
     * <p>동사를 열거하는 방식이라 <b>원리적으로 빈틈이 있다.</b> 처음 판에서는
     * {@code ALTER TABLE}·{@code CREATE TABLE}·{@code INSERT INTO}·{@code UPDATE}·
     * {@code REFERENCES}·{@code FROM}·{@code JOIN}·{@code COMMENT ON TABLE} 여덟 개만
     * 봤는데, 반박 리뷰가 {@code MERGE INTO}·{@code TRUNCATE TABLE}·{@code DROP TABLE}·
     * {@code CREATE SEQUENCE}·{@code CREATE VIEW}·{@code CREATE SYNONYM} 등 12종이
     * 그대로 통과하는 것을 실측으로 보여 줬다. 여기 목록은 그 지적을 반영한 것이고,
     * 오라클 마이그레이션에 나올 수 있는 동사를 넓게 덮는다. 새 동사가 생기면 여기도
     * 함께 늘려야 한다.
     */
    private static final Pattern 객체_자리 = Pattern.compile("(?i)\\b("
            + "ALTER\\s+TABLE"
            + "|CREATE\\s+(?:GLOBAL\\s+TEMPORARY\\s+|PRIVATE\\s+TEMPORARY\\s+)?TABLE"
            + "|DROP\\s+TABLE|TRUNCATE\\s+TABLE|RENAME\\s+TABLE"
            + "|INSERT\\s+INTO|MERGE\\s+INTO|UPDATE|DELETE\\s+FROM|REFERENCES|FROM|JOIN"
            + "|COMMENT\\s+ON\\s+TABLE"
            + "|CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:MATERIALIZED\\s+)?VIEW|DROP\\s+VIEW"
            + "|CREATE\\s+SEQUENCE|DROP\\s+SEQUENCE|ALTER\\s+SEQUENCE"
            + "|CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:PUBLIC\\s+)?SYNONYM|DROP\\s+SYNONYM"
            + "|CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:PROCEDURE|FUNCTION|PACKAGE|TRIGGER|TYPE)"
            + "|DROP\\s+INDEX|ALTER\\s+INDEX"
            + ")\\s+" + 한정된_이름);

    /** {@code CREATE INDEX ... ON schema.table} */
    private static final Pattern 인덱스_대상 = Pattern.compile(
            "(?i)\\bCREATE\\s+(?:UNIQUE\\s+|BITMAP\\s+)?INDEX\\s+" + 이름 + "\\s+ON\\s+"
                    + 한정된_이름);

    /** {@code CREATE SYNONYM x FOR schema.table} — 한정자가 FOR 뒤에 온다. */
    private static final Pattern 시노님_대상 = Pattern.compile(
            "(?i)\\bCREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:PUBLIC\\s+)?SYNONYM\\s+" + 이름
                    + "\\s+FOR\\s+" + 한정된_이름);

    /** {@code GRANT ... ON schema.table TO ...} — 문장 안 어디든 ON 뒤가 한정돼 있으면 잡는다. */
    private static final Pattern 권한_대상 = Pattern.compile(
            "(?is)\\bGRANT\\b[^;]*?\\bON\\s+" + 한정된_이름);

    /** {@code COMMENT ON COLUMN} 은 table.column 이라 점 하나까지가 정상이다. 둘 이상이면 스키마다. */
    private static final Pattern 컬럼_주석 = Pattern.compile(
            "(?i)\\bCOMMENT\\s+ON\\s+COLUMN\\s+(" + 이름 + "(?:\\s*\\.\\s*" + 이름 + "){2,})");

    /** DB 링크 — {@code FROM BRANCH@OLDDB}. 배포처를 특정 DB 에 묶는다는 점에서 같은 문제다. */
    private static final Pattern 디비_링크 = Pattern.compile(
            "(?i)(" + 이름 + "\\s*@\\s*[A-Za-z0-9_$.]+)");

    /**
     * {@code ALTER SESSION SET CURRENT_SCHEMA = ...} — 이 가드의 목적을 통째로 무력화한다.
     *
     * <p>한 줄이면 이후 모든 문장이 특정 스키마에서 돈다. 반박 리뷰가 찾은 것 중 가장 나쁜
     * 되돌림이라 한정자 검사와 별개로 존재 자체를 금지한다.
     */
    private static final Pattern 세션_스키마 = Pattern.compile(
            "(?i)(\\bALTER\\s+SESSION\\s+SET\\s+CURRENT_SCHEMA\\b)");

    /**
     * UG-296: 오라클 마이그레이션 SQL 에 스키마 한정자를 쓰지 않는다.
     *
     * <p>match 의 {@code V2}/{@code V3} 가 {@code UNIVS."DESCRIPTOR"} 처럼 스키마를 박아 두고
     * 있었다. 같은 파일의 {@code V1} 은 프리픽스 없이 테이블을 만들기 때문에, 접속 계정이
     * {@code UNIVS} 가 아닌 순간 {@code V2} 첫 줄에서 ORA-00942 로 멈춘다. 개발 계정 이름이
     * 우연히 {@code univs} 라 드러나지 않았을 뿐이다.
     *
     * <p>온프레미스는 서비스마다 오라클 계정을 따로 판다 — 오라클은 계정과 스키마가 1:1 이라
     * 그렇게 하지 않으면 다섯 서비스가 {@code flyway_schema_history} 를 공유하게 되고, 두
     * 번째로 뜨는 서비스가 자기 {@code V1} 의 체크섬 불일치로 기동에 실패한다. 그래서 스키마
     * 이름은 접속 계정이 정하게 두고 SQL 에는 적지 않는다.
     *
     * <p>주석은 먼저 걷어낸다. 위 사정을 설명하는 주석 자체에 {@code UNIVS.} 가 들어 있다.
     */
    @Test
    @DisplayName("오라클 마이그레이션에 스키마 한정자가 없다")
    void 오라클_마이그레이션에_스키마_한정자가_없다() throws IOException {
        List<String> 위반 = new ArrayList<>();

        for (Path sql : 오라클_마이그레이션_파일들()) {
            String 본문 = 주석을_지운다(Files.readString(sql, StandardCharsets.UTF_8));
            모은다(위반, sql, 본문, 객체_자리, 2, "객체 자리");
            모은다(위반, sql, 본문, 인덱스_대상, 1, "인덱스 대상");
            모은다(위반, sql, 본문, 권한_대상, 1, "GRANT 대상");
            모은다(위반, sql, 본문, 시노님_대상, 1, "시노님 대상");
            모은다(위반, sql, 본문, 컬럼_주석, 1, "COMMENT ON COLUMN");
            모은다(위반, sql, 본문, 디비_링크, 1, "DB 링크");
            모은다(위반, sql, 본문, 세션_스키마, 1, "세션 스키마 고정");
        }

        assertThat(위반)
                .as("스키마는 접속 계정이 정한다. SQL 에 박으면 그 계정에서만 도는 마이그레이션이 "
                        + "되고, 서비스별 계정으로 납품하는 온프레미스에서 ORA-00942 로 죽는다")
                .isEmpty();
    }

    /**
     * UG-296: 이 서비스의 오라클 계정이 다른 서비스와 겹치지 않는다.
     *
     * <p>다섯 서비스가 모두 {@code username: univs} 를 쓰고 있었다. 오라클에서 그것은 한
     * 스키마를 다섯이 나눠 쓴다는 뜻이고, {@code flyway_schema_history} 도 하나가 된다. 각
     * 서비스의 {@code V1__init.sql} 은 서로 다르므로 두 번째로 뜨는 서비스부터
     * {@code Migration checksum mismatch for migration version 1} 로 기동에 실패한다.
     *
     * <p>PostgreSQL 은 서비스별 데이터베이스를 쓰고 있어 같은 문제가 없다. 오라클만의 함정이다.
     *
     * <p>부분 문자열이 아니라 <b>정확히 일치</b>를 본다. 리뷰가 {@code univs_gateway} 나
     * {@code gate_face_palm_match} 같은 값이 {@code contains} 검사를 통과하는 것을 보여 줬다.
     *
     * <p><b>이 가드가 덮는 범위는 레포 안의 개발 기본값까지다.</b> 실제 컨테이너는 compose 가
     * 넘기는 {@code SPRING_DATASOURCE_USERNAME} 환경변수를 쓰고, 그쪽이 config-server 값보다
     * 우선한다 ({@code spring.config.import} 는 bootstrap 이 아니라 config-data 경로다).
     * 실배포의 계정 분리는 {@code infra/docker/compose/compose.*.yml} 과 {@code .env} 가 쥐고
     * 있으므로 {@code docs/onpremise-oracle-setup.md} 를 함께 볼 것.
     */
    @Test
    @DisplayName("오라클 계정이 이 서비스 전용이다")
    void 오라클_계정이_서비스_전용이다() throws IOException {
        String username = binderFor("application-oracle.yml")
                .bind("spring.datasource.username", String.class)
                .orElse("");

        assertThat(username)
                .as("다른 서비스의 yml 을 복사해 오거나 공용 계정으로 되돌리는 것을 막는다. "
                        + "오라클은 계정 = 스키마다")
                .isEqualTo(ORACLE_ACCOUNT);
    }

    /**
     * 오라클 마이그레이션 파일 전부.
     *
     * <p>{@code Files.list} 가 아니라 {@code Files.walk} 다. Flyway 의 위치 스캔은 재귀라
     * {@code oracle/extra/V98__x.sql} 도 실행하는데, 1단계만 보면 그 파일은 가드를 통과한다
     * (반박 리뷰가 실측으로 보여 줬다).
     */
    private static List<Path> 오라클_마이그레이션_파일들() throws IOException {
        Path dir = new ClassPathResource("db/migration/oracle").getFile().toPath();
        try (Stream<Path> paths = Files.walk(dir)) {
            List<Path> sqls = paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            assertThat(sqls)
                    .as("오라클 마이그레이션을 한 개도 못 찾았다면 이 가드는 아무것도 검사하지 않는다")
                    .isNotEmpty();
            return sqls;
        }
    }

    private static String 주석을_지운다(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("--[^\\n]*", " ");
    }

    private static void 모은다(
            List<String> 위반, Path sql, String 본문, Pattern pattern, int group, String 자리) {
        Matcher m = pattern.matcher(본문);
        while (m.find()) {
            위반.add("%s: %s '%s'".formatted(sql.getFileName(), 자리, m.group(group)));
        }
    }
}
