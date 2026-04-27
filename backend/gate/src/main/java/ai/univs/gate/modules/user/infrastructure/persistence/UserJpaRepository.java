package ai.univs.gate.modules.user.infrastructure.persistence;

import ai.univs.gate.modules.user.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<User, Long> {

    Optional<User> findByIdAndIsDeleted(Long id, boolean isDeleted);

    Optional<User> findByFaceIdAndProjectIdAndIsDeleted(String faceId, Long projectId, boolean isDeleted);

    long countByProjectIdAndIsDeleted(Long projectId, boolean isDeleted);
}
