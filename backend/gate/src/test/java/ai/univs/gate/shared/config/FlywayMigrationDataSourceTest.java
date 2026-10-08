package ai.univs.gate.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/**
 * gate-config 의 DB 종류별 파일이 기대는 Spring Boot 동작 (UG-367).
 *
 * <p>그 파일들은 커넥션 풀에 소켓 읽기 상한({@code socketTimeout} 등)을 걸고, Flyway 에는
 * {@code spring.flyway.url/user/password} 를 준다. 그러면 Flyway 가 풀이 아닌 별도 연결(SimpleDriverDataSource)을
 * 써서 상한을 받지 않는다 — 큰 테이블 마이그레이션이 상한에 걸려 기동이 실패하지 않게. 이 동작이 바뀌면(부트 업그레이드)
 * 온프레미스 업그레이드 설치가 마이그레이션 중에 멈출 수 있으므로 여기서 못박는다.
 */
@DisplayName("UG-367: Flyway 는 상한이 걸린 풀이 아닌 별도 연결로 마이그레이션한다")
class FlywayMigrationDataSourceTest {

    private static final String URL = "jdbc:h2:mem:ug367;DB_CLOSE_DELAY=-1";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class))
            .withPropertyValues(
                    "spring.datasource.url=" + URL,
                    "spring.datasource.username=sa",
                    "spring.datasource.password=",
                    "spring.datasource.hikari.data-source-properties.socketTimeout=10",
                    "spring.flyway.locations=classpath:ug367-none",
                    // gate-config 의 {서비스}-postgresql.yml · -oracle.yml 과 같은 줄
                    "spring.flyway.url=${spring.datasource.url}",
                    "spring.flyway.user=${spring.datasource.username}",
                    "spring.flyway.password=${spring.datasource.password}");

    @Test
    @DisplayName("앱의 풀은 상한을 받고, Flyway 는 풀이 아닌 SimpleDriverDataSource 를 쓴다")
    void 별도_연결() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DataSource.class)).isInstanceOf(HikariDataSource.class);
            HikariDataSource pool = (HikariDataSource) context.getBean(DataSource.class);
            assertThat(pool.getDataSourceProperties()).containsEntry("socketTimeout", "10");

            DataSource migration = context.getBean(Flyway.class).getConfiguration().getDataSource();
            assertThat(migration).isInstanceOf(SimpleDriverDataSource.class).isNotSameAs(pool);
            assertThat(((SimpleDriverDataSource) migration).getUrl()).isEqualTo(URL);
        });
    }

    @Test
    @DisplayName("대조군: Flyway 연결을 따로 주지 않으면 상한이 걸린 그 풀로 마이그레이션한다")
    void 대조군() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=" + URL, "spring.datasource.username=sa",
                        "spring.flyway.locations=classpath:ug367-none")
                .run(context -> assertThat(context.getBean(Flyway.class).getConfiguration().getDataSource())
                        .isSameAs(context.getBean(DataSource.class)));
    }
}
