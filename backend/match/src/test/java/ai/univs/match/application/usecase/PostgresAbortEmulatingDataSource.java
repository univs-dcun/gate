package ai.univs.match.application.usecase;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * H2 위에서 PostgreSQL 의 「오류 난 트랜잭션은 중단된다」 를 흉내 내는 데이터소스 (UG-372 테스트 전용).
 *
 * <p>H2 와 Oracle 은 문장 하나가 실패해도 트랜잭션은 계속 쓸 수 있다. PostgreSQL 은 아니다 — 그 뒤 문장은 모두
 * {@code 25P02} 로 거절되고, COMMIT 은 오류 없이 ROLLBACK 이 된다(pgjdbc 기본값은 이것을 예외로 올리지 않는다).
 * UG-340 의 진 쪽이 브랜치를 다시 읽지 못한 이유가 이것이다. H2 그대로면 "같은 트랜잭션에서 잡고 다시 읽기" 같은 잘못된
 * 구현도 통과하므로, 커넥션을 감싸 같은 규칙을 씌운다:
 * <ul>
 *   <li>자동 커밋이 꺼진 커넥션에서 문장이 실패하면 중단 상태가 된다.
 *   <li>중단 상태에서 실행하는 문장은 {@code 25P02} 로 거절한다({@link #rejectedInAbortedTransaction}).
 *   <li>중단 상태의 commit 은 조용히 rollback 한다({@link #silentRollbacks}). rollback·자동 커밋 켜기로 풀린다.
 * </ul>
 *
 * <p>경합을 정해진 자리에 넣는 갈고리 두 개도 둔다 — 브랜치 INSERT 직전(1회용), 브랜치 이름 조회 직후.
 */
final class PostgresAbortEmulatingDataSource extends DelegatingDataSource {

    /** 브랜치 INSERT 가 나가기 직전에 한 번 실행한다 — 다른 트랜잭션이 같은 이름을 먼저 커밋하게 할 자리. */
    final AtomicReference<Runnable> beforeBranchInsert = new AtomicReference<>();

    /** 브랜치 이름 조회가 끝날 때마다 실행한다 (커넥션을 쥔 채). */
    volatile Runnable afterBranchSelect = () -> { };

    /** 브랜치 INSERT 가 {@code uk_branch_branch_name} 위반으로 실패한 횟수 — 경합이 실제로 일어났는가. */
    final AtomicInteger branchNameViolations = new AtomicInteger();

    /** 중단된 트랜잭션에서 문장을 실행하려 한 횟수 — PostgreSQL 이었으면 25P02. */
    final AtomicInteger rejectedInAbortedTransaction = new AtomicInteger();

    /** 중단된 트랜잭션을 커밋하려 한 횟수 — PostgreSQL 이었으면 조용히 버려진 쓰기. */
    final AtomicInteger silentRollbacks = new AtomicInteger();

    PostgresAbortEmulatingDataSource(DataSource target) {
        super(target);
    }

    void reset() {
        beforeBranchInsert.set(null);
        afterBranchSelect = () -> { };
        branchNameViolations.set(0);
        rejectedInAbortedTransaction.set(0);
        silentRollbacks.set(0);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(obtainTargetDataSource().getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(obtainTargetDataSource().getConnection(username, password));
    }

    /** 커넥션 하나의 트랜잭션 상태. */
    private static final class TxState {
        volatile boolean aborted;
    }

    private Connection wrap(Connection target) {
        TxState state = new TxState();
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "commit":
                            if (state.aborted) {
                                state.aborted = false;
                                silentRollbacks.incrementAndGet();
                                target.rollback();
                                return null;
                            }
                            break;
                        case "rollback":
                            state.aborted = false;
                            break;
                        case "setAutoCommit":
                            if (Boolean.TRUE.equals(args[0]) && state.aborted) {
                                state.aborted = false;
                                silentRollbacks.incrementAndGet();
                                target.rollback();
                            }
                            break;
                        default:
                            break;
                    }
                    Object result = invoke(target, method, args);
                    if (result instanceof Statement statement && method.getReturnType().isInterface()
                            && Statement.class.isAssignableFrom(method.getReturnType())) {
                        String sql = args != null && args.length > 0 && args[0] instanceof String s ? s : "";
                        return wrapStatement(statement, method.getReturnType(), sql, target, state);
                    }
                    return result;
                });
    }

    private Object wrapStatement(Statement target, Class<?> type, String preparedSql, Connection connection,
                                 TxState state) {
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            String name = method.getName();
            if (name.equals("equals")) {
                return proxy == args[0];
            }
            if (name.equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (!name.startsWith("execute")) {
                return invoke(target, method, args);
            }
            String sql = !preparedSql.isEmpty() ? preparedSql
                    : args != null && args.length > 0 && args[0] instanceof String s ? s : "";
            String lower = sql.toLowerCase(Locale.ROOT);
            if (state.aborted) {
                rejectedInAbortedTransaction.incrementAndGet();
                throw new SQLException(
                        "ERROR: current transaction is aborted, commands ignored until end of transaction block", "25P02");
            }
            boolean branchInsert = lower.startsWith("insert into branch ") || lower.startsWith("insert into branch(");
            if (branchInsert) {
                Runnable hook = beforeBranchInsert.getAndSet(null);
                if (hook != null) {
                    hook.run();
                }
            }
            try {
                Object result = invoke(target, method, args);
                if (lower.contains(" from branch ") && lower.contains("branch_name")) {
                    afterBranchSelect.run();
                }
                return result;
            } catch (SQLException e) {
                if (!connection.getAutoCommit()) {
                    state.aborted = true;
                }
                if (branchInsert && String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT)
                        .contains("uk_branch_branch_name")) {
                    branchNameViolations.incrementAndGet();
                }
                throw e;
            }
        });
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
