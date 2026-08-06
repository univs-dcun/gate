package ai.univs.match.shared.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import org.flywaydb.core.extensibility.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * UG-296: 오라클 마이그레이션이 실행될 수 있는 상태인지 지킨다.
 *
 * <p>Flyway 10 부터 DB 지원이 모듈로 분리됐다. {@code flyway-core} 하나만으로는 오라클을 모른다.
 * 이 레포는 {@code flyway-database-postgresql} 만 선언한 채로 {@code db/migration/oracle} 에
 * SQL 을 쌓아 왔고, 그래서 <b>그 SQL 은 한 번도 실행된 적이 없다</b> — 오라클 URL 로 기동하면
 * {@code FlywayException: No database found to handle jdbc:oracle:thin:@...} 로 부팅 시점에
 * 죽는다. SQL 을 한 줄도 파싱하기 전이다.
 *
 * <p>UG-292 가 고친 오라클 SQL 의 절 순서 오류가 아무 신호도 내지 않은 진짜 이유가 이것이다.
 *
 * <p>이 테스트가 막는 것은 <b>의존성이 사라지는 것</b>까지다. SQL 이 실제 오라클에서 끝까지 도는지는
 * 여기서 볼 수 없다 (UG-296 의 남은 항목). CI 에 오라클 인스턴스가 붙기 전까지, 최소한 조용히
 * 되돌아가는 것은 여기서 걸린다.
 *
 * <p>Flyway 가 실제로 쓰는 발견 경로
 * ({@code META-INF/services/org.flywaydb.core.extensibility.Plugin})를 그대로 확인한다.
 * 클래스 존재만 보면 SPI 등록이 빠져도 통과하고, {@code DatabaseTypeRegister} 는 내부 API 라
 * 10.x 와 11.x 의 시그니처가 달라 서비스마다 다른 코드를 써야 한다.
 */
@DisplayName("UG-296: Flyway 오라클 지원 모듈")
class FlywayOracleSupportTest {

    private static final String ORACLE = "org.flywaydb.database.oracle.OracleDatabaseType";
    private static final String POSTGRESQL = "org.flywaydb.database.postgresql.PostgreSQLDatabaseType";

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
     * 마이그레이션 위치가 오라클 폴더를 가리키는지 (리뷰 지적).
     *
     * <p>플러그인이 있어도 {@code application-oracle.yml} 의
     * {@code spring.flyway.locations} 가 사라지면 Flyway 는 기본값
     * {@code classpath:db/migration} 으로 되돌아간다. 그 폴더는 없으므로 <b>아무것도 실행하지
     * 않고 조용히 성공한다.</b> 부팅이 되니 더 알아채기 어렵다.
     *
     * <p>스프링 컨텍스트를 띄우지 않고 리소스 원문을 읽는다 — 이 서비스에는 슬라이스 테스트
     * 인프라가 없다 (UG-300).
     */
    @Test
    @DisplayName("오라클 프로파일이 db/migration/oracle 을 가리킨다")
    void 오라클_프로파일이_오라클_폴더를_가리킨다() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/application-oracle.yml")) {
            assertThat(in)
                    .as("application-oracle.yml 이 사라지면 오라클 프로파일 자체가 동작하지 않는다")
                    .isNotNull();

            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(yml)
                    .as("이 설정이 없으면 Flyway 가 db/migration 으로 되돌아가 아무것도 실행하지 않는다")
                    .contains("classpath:db/migration/oracle");
        }
    }
}
