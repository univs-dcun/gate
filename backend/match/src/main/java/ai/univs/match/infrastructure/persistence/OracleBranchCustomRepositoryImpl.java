package ai.univs.match.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;

@Profile("oracle")
@RequiredArgsConstructor
public class OracleBranchCustomRepositoryImpl implements BranchCustomRepository {

    private final EntityManager em;

    /**
     * {@code IGNORE_ROW_ON_DUPKEY_INDEX} 는 고유 인덱스 위반 행을 오류 없이 건너뛰는 Oracle 의 의미 힌트다(11gR2+). 같은 이름이
     * 아직 커밋되지 않았으면 그 트랜잭션이 끝나길 기다렸다가, 커밋됐으면 0 건으로 끝난다.
     *
     * <p>{@code MERGE ... WHEN NOT MATCHED} 를 먼저 시도했지만 버렸다 — 동시에 돌면 둘 다 "없음" 으로 보고 INSERT 해, 실제
     * Oracle 23 에서 동시 16건 중 6건이 위반(ERROR 줄)이었다. 이 힌트로는 0건이었다.
     */
    @Override
    public int insertIfAbsent(String branchName, LocalDateTime now) {
        return em.createNativeQuery("""
                    INSERT /*+ IGNORE_ROW_ON_DUPKEY_INDEX(branch(branch_name)) */
                    INTO branch (branch_name, created_at, modified_at)
                    VALUES (:branchName, :now, :now)
                """)
                .setParameter("branchName", branchName)
                .setParameter("now", now)
                .executeUpdate();
    }
}
