package ai.univs.gate.shared.exception;

import java.io.InterruptedIOException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;

/**
 * DB 커넥션 풀에서 커넥션을 제때 얻지 못한 실패인지 가린다 (UG-359).
 *
 * <p>HikariCP 는 {@code connection-timeout} 안에 커넥션을 내주지 못하면
 * {@link SQLTransientConnectionException} 을 던진다. 그 위를 Spring 이 감싼다 — 트랜잭션을 열다 났으면
 * {@code CannotCreateTransactionException}, JDBC 접근 중이면 {@code DataAccessException} 계열. 감싸는 층은
 * 경로마다 다르므로 타입이 아니라 <b>원인 사슬</b>에서 찾는다.
 *
 * <p>예전에는 이것이 catch-all 로 떨어져 500 + ERROR 스택트레이스였다. 버스트에서 풀이 잠깐 모자란 것은
 * "다시 보내면 되는 실패" 인데 클라이언트도 운영자도 그것을 알 수 없었다. 이제는 503 + {@code Retry-After}
 * 로 내보내고, 로그 수준은 아래 {@link #isCongestion} 으로 가른다.
 *
 * <p>같은 클래스가 face·match·palm 에도 있다 — 서비스마다 gradle 프로젝트가 따로라 공유 모듈이 없다.
 */
public final class PoolExhaustion {

    /** 원인 사슬을 따라가는 상한. 정상적인 사슬은 열 단계를 넘지 않는다 — 상한은 순환 방어의 이중 장치다. */
    private static final int MAX_DEPTH = 32;

    private PoolExhaustion() {
    }

    /**
     * 원인 사슬에서 풀 타임아웃을 찾는다. 없으면 비어 있다.
     *
     * <p>순환하는 원인 사슬({@code a.cause = b, b.cause = a})이 있어도 끝난다 — 이미 본 예외는 다시 보지
     * 않는다. 예외 처리 중에 무한 루프에 빠지면 원래 오류가 통째로 가려지고 요청 스레드가 묶인다.
     */
    public static Optional<SQLTransientConnectionException> find(Throwable ex) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth++ < MAX_DEPTH && seen.add(current)) {
            if (current instanceof SQLTransientConnectionException timeout) {
                return Optional.of(timeout);
            }
            current = current.getCause();
        }
        return Optional.empty();
    }


    /**
     * DB 가 정해진 시간 안에 응답하지 않은 실패인지 가린다 (UG-367). 원인 사슬에서 {@link SQLException} 을 찾고, 그
     * 아래에 읽기 타임아웃({@link InterruptedIOException})이 있으면 그 SQLException 을 돌려준다.
     *
     * <p>JDBC 드라이버의 소켓 읽기 상한(PostgreSQL {@code socketTimeout}, Oracle {@code oracle.jdbc.ReadTimeout}) 에
     * 걸리면 PostgreSQL 은 {@code SocketTimeoutException}, Oracle 은 {@code IOReadTimeoutException} 을 원인으로 싣는다
     * — 둘 다 {@code InterruptedIOException} 이다. 이 상한이 없으면 DB 가 멈췄을 때 이미 커넥션을 쥔 쿼리가 끝없이
     * 기다렸다(scaling 측정 C, 온프레미스 3.0.17 에서 목록 조회 40초 무응답).
     *
     * <p>SQLException 아래에 있어야 한다 — 하위 서비스 호출(Feign)의 읽기 타임아웃처럼 DB 와 무관한 타임아웃을 잡지 않는다.
     * 풀 타임아웃({@link #find})이 먼저다: 그 원인에도 연결 시도의 타임아웃이 실릴 수 있다.
     */
    public static Optional<SQLException> findReadTimeout(Throwable ex) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = ex;
        SQLException sql = null;
        int depth = 0;
        while (current != null && depth++ < MAX_DEPTH && seen.add(current)) {
            if (sql == null && current instanceof SQLException found) {
                sql = found;
            } else if (sql != null && current instanceof InterruptedIOException) {
                return Optional.of(sql);
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    /**
     * 풀이 붐볐을 뿐인가 — 원인이 없는 타임아웃.
     *
     * <p>Hikari 는 타임아웃 예외의 원인에 <b>최근의 커넥션 생성 실패</b>를 싣는다
     * ({@code HikariPool.createTimeoutException} 의 {@code getLastConnectionFailure()}). 생성이 성공하면 그
     * 값은 지워진다. 그러므로
     * <ul>
     *   <li>원인 없음 — 커넥션은 다 살아 있는데 모두 빌려 나가 있었다. 버스트에서 예상되는 일이다. WARN.
     *   <li>원인 있음 — 새 커넥션을 만들지 못하고 있다(DB 다운·연결 거부·인증 실패). 사람이 봐야 한다.
     *       ERROR + 스택트레이스.
     * </ul>
     * 응답은 둘 다 503 이다 — 클라이언트에게는 "잠시 뒤 다시" 가 맞는 안내다.
     */
    public static boolean isCongestion(SQLTransientConnectionException timeout) {
        return timeout.getCause() == null;
    }
}
