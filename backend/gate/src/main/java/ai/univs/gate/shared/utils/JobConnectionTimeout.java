package ai.univs.gate.shared.utils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 정리 잡의 트랜잭션만 DB 응답 상한을 늘린다 (UG-367).
 *
 * <p>gate 의 커넥션은 DB 응답을 {@code socketTimeout}(gate-config 의 DB 종류별 파일, 10초)까지만 기다린다. DB 가 멈췄을 때
 * 요청이 끝없이 매달리지 않게 하려는 것이다. 그런데 퍼지·정리 잡에는 인덱스 없이 큰 테이블을 훑는 조회가 있어(이미지 경로
 * 참조 확인 등) 데이터가 많으면 10초를 넘길 수 있다. 그 잡들은 요청 경로가 아니라 기다려도 되므로, 트랜잭션을 연 직후
 * 이것을 불러 그 커넥션만 {@link #JOB} 까지 기다리게 한다.
 *
 * <p>HikariCP 는 커넥션을 풀에 돌려받을 때 바꾼 네트워크 타임아웃을 원래 값으로 되돌린다 — 다른 요청이 늘어난 값을 물려받지
 * 않는다.
 *
 * <p>PostgreSQL 은 서버 쪽 {@code statement_timeout}(gate-config, 소켓 상한보다 조금 짧게)도 걸려 있어 함께 늘린다.
 * {@code SET LOCAL} 이라 이 트랜잭션이 끝나면 원래 값으로 돌아간다.
 */
@Slf4j
@Component
public class JobConnectionTimeout {

    /** 정리 잡이 DB 응답을 기다리는 상한. 멈춘 DB 를 영원히 기다리지는 않는다. */
    public static final Duration JOB = Duration.ofMinutes(5);
    /** 서버 쪽 취소는 소켓 상한보다 조금 먼저 — 서버가 취소하면 커넥션을 버리지 않아도 된다 (델타 리뷰 L1). */
    static final Duration JOB_STATEMENT = JOB.minusSeconds(10);

    private final DataSource dataSource;

    public JobConnectionTimeout(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 지금 트랜잭션의 커넥션에 {@link #JOB} 을 건다. 트랜잭션 밖이면 아무것도 하지 않는다 — 걸 커넥션이 없고, 여기서 새로
     * 빌리면 곧바로 반납돼 의미가 없다.
     *
     * <p>바꾸지 못해도(드라이버 미지원 등) 잡을 멈추지 않는다 — 기본 상한으로 돌 뿐이다.
     */
    public void extendForCurrentTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            try {
                connection.setNetworkTimeout(Runnable::run, (int) JOB.toMillis());
            } catch (SQLException | RuntimeException e) {
                log.warn("정리 잡의 DB 응답 상한을 늘리지 못했다 — 기본 상한으로 진행한다. 원인={}", e.toString());
            }
            extendStatementTimeout(connection);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    /**
     * PostgreSQL 이면 이 트랜잭션의 {@code statement_timeout} 을 늘린다. 실패하면 던진다 — PostgreSQL 은 실패한 문장 뒤의
     * 트랜잭션을 버린 상태(aborted)로 두므로, 삼키고 진행하면 이후 문장이 모두 엉뚱한 원인(25P02)으로 실패한다 (델타 리뷰 L2).
     */
    private static void extendStatementTimeout(Connection connection) {
        try {
            if (!isPostgreSql(connection)) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL statement_timeout = " + JOB_STATEMENT.toMillis());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("정리 잡의 statement_timeout 을 늘리지 못했다", e);
        }
    }

    private static boolean isPostgreSql(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        return metaData != null && "PostgreSQL".equalsIgnoreCase(metaData.getDatabaseProductName());
    }
}
