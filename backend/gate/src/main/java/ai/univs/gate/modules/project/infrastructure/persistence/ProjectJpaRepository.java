package ai.univs.gate.modules.project.infrastructure.persistence;

import ai.univs.gate.modules.project.domain.entity.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface ProjectJpaRepository extends JpaRepository<Project, Long> {

    Project save(Project project);

    Optional<Project> findByIdAndIsDeletedFalse(Long id);

    /**
     * 같은 프로젝트를 동시에 고치는 요청을 직렬화하기 위한 행 잠금 (UG-302).
     *
     * <p>지금 잠그는 쓰기 경로는 프로젝트 삭제뿐이다. UG-311 이 프로젝트 수정을 여기에 합류시킬
     * 예정이다 — 삭제가 커밋된 뒤 먼저 시작된 수정이 더티 체킹으로 {@code is_deleted} 를 되써
     * 프로젝트를 부활시키기 때문이다.
     *
     * <p>키 행이 아니라 <b>프로젝트 행</b>을 잠근다. {@code WHERE is_active = true} 로 키를
     * 잠그면, 뒤에 온 요청이 잠금을 얻는 순간 그 행은 이미 비활성이라 조건에서 빠져 아무것도
     * 못 잡는다 — 직렬화가 되지 않는다. 프로젝트 행은 두 요청 모두에게 안정적으로 존재한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Project> findForUpdateByIdAndIsDeletedFalse(Long id);

    long countByAccountIdAndIsDeletedFalse(Long userId);
}
