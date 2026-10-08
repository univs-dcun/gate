package ai.univs.palm.shared.exception;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * 커넥션이 이미 끊겨 롤백하지 못한 실패가 원래 예외를 덮지 않게 한다 (UG-367 반박 리뷰 H1).
 *
 * <p>DB 응답 상한({@code socketTimeout})에 걸리면 HikariCP 는 그 커넥션을 끊긴 것으로 보고 닫는다. 이어서 Spring 이
 * 트랜잭션을 롤백하려 하면 {@code Connection is closed}(SQLState 08003)로 실패하고, 그 롤백 예외가 원래 예외를
 * 대신해 던져진다({@code TransactionAspectSupport} 의 "Application exception overridden by rollback exception").
 * 그러면 처리기는 읽기 타임아웃을 보지 못해 503 대신 500 을 냈다 — 트랜잭션 안에서 난 타임아웃은 거의 전부 그랬다.
 *
 * <p>연결이 끊기면 DB 서버가 그 트랜잭션을 스스로 롤백하므로, 이 경우의 롤백 실패는 삼키고 WARN 만 남긴다. 그 밖의
 * 롤백 실패는 예전처럼 던진다.
 *
 * <p>같은 클래스가 gate·face·match·palm 에 있다 — 서비스마다 gradle 프로젝트가 따로라 공유 모듈이 없다.
 */
@Slf4j
public class ConnectionLossTolerantJpaTransactionManager extends JpaTransactionManager {

    private static final int MAX_DEPTH = 32;

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        try {
            super.doRollback(status);
        } catch (RuntimeException e) {
            if (!isConnectionLost(e)) {
                throw e;
            }
            log.warn("롤백할 커넥션이 이미 끊겼다 — DB 가 트랜잭션을 버리므로 원래 예외를 그대로 올린다. 원인={}", e.toString());
        }
    }

    /** 원인 사슬에 연결 계열 SQLState(08xxx)를 가진 SQLException 이 있는가. */
    static boolean isConnectionLost(Throwable ex) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth++ < MAX_DEPTH && seen.add(current)) {
            if (current instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("08")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
