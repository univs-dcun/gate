package ai.univs.gate.modules.user.infrastructure.persistence;

import ai.univs.gate.modules.user.application.input.UserQuery;
import ai.univs.gate.modules.user.domain.entity.User;
import ai.univs.gate.modules.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;
    private final UserDSLRepository userDSLRepository;

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }

    @Override
    public Optional<User> findByIdAndIsDeletedFalse(Long userId) {
        return userJpaRepository.findByIdAndIsDeleted(userId, false);
    }

    @Override
    public Optional<User> findByFaceIdAndProjectIdAndIsDeletedFalse(String faceId, Long projectId) {
        return userJpaRepository.findByFaceIdAndProjectIdAndIsDeleted(faceId, projectId, false);
    }

    @Override
    public Page<User> findAllByQuery(UserQuery query, Long projectId) {
        return userDSLRepository.findAllByQuery(query, projectId);
    }

    @Override
    public long countByProjectIdAndIsDeletedFalse(Long projectId) {
        return userJpaRepository.countByProjectIdAndIsDeleted(projectId, false);
    }
}
