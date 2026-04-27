package ai.univs.gate.modules.user.domain.repository;

import ai.univs.gate.modules.user.application.input.UserQuery;
import ai.univs.gate.modules.user.domain.entity.User;
import org.springframework.data.domain.Page;

import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findByIdAndIsDeletedFalse(Long userId);

    Optional<User> findByFaceIdAndProjectIdAndIsDeletedFalse(String faceId, Long projectId);

    Page<User> findAllByQuery(UserQuery query, Long projectId);

    long countByProjectIdAndIsDeletedFalse(Long projectId);
}