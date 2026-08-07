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
     * <p>API 키 재발급은 "활성 키를 끄고 새로 하나 넣는다" 인데, 잠금이 없으면 두 요청이 같은
     * 활성 키를 읽고 각자 새 키를 넣어 <b>활성 2개</b>가 남는다. 그 상태가 되면 프로젝트 상세
     * 조회와 재발급이 예전에는 둘 다 500 이 됐다.
     *
     * <p>키 행이 아니라 <b>프로젝트 행</b>을 잠근다. {@code WHERE is_active = true} 로 키를
     * 잠그면, 뒤에 온 요청이 잠금을 얻는 순간 그 행은 이미 비활성이라 조건에서 빠져 아무것도
     * 못 잡는다 — 직렬화가 되지 않는다. 프로젝트 행은 두 요청 모두에게 안정적으로 존재한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Project> findForUpdateByIdAndIsDeletedFalse(Long id);

    long countByAccountIdAndIsDeletedFalse(Long userId);
}
