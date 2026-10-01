package ai.univs.gate.modules.webhook.infrastructure.persistence;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/** Hibernate 가 실제로 내보내는 SQL 을 모은다 — 잠금 조회의 렌더링을 보려고 쓴다 (UG-344 반박 리뷰 B1). */
public class RecordingStatementInspector implements StatementInspector {

    static final List<String> SQL = new CopyOnWriteArrayList<>();

    /**
     * 원문을 기록하고, H2 가 모르는 PostgreSQL 전용 잠금 절({@code FOR NO KEY UPDATE})만 같은 의미의
     * {@code FOR UPDATE} 로 바꿔 실행시킨다 — 보려는 것은 렌더링이지 H2 의 잠금이 아니다.
     */
    @Override
    public String inspect(String sql) {
        SQL.add(sql);
        return sql.replace(" for no key update", " for update");
    }
}
