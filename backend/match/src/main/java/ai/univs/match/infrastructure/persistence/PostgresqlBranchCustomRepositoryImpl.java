package ai.univs.match.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;

@Profile("postgresql")
@RequiredArgsConstructor
public class PostgresqlBranchCustomRepositoryImpl implements BranchCustomRepository {

    private final EntityManager em;

    /** {@code ON CONFLICT (branch_name)} 은 {@code uk_branch_branch_name}(V5) 이 있어야 쓸 수 있다. */
    @Override
    public int insertIfAbsent(String branchName, LocalDateTime now) {
        return em.createNativeQuery("""
                    INSERT INTO branch (branch_name, created_at, modified_at)
                    VALUES (:branchName, :now, :now)
                    ON CONFLICT (branch_name) DO NOTHING
                """)
                .setParameter("branchName", branchName)
                .setParameter("now", now)
                .executeUpdate();
    }
}
