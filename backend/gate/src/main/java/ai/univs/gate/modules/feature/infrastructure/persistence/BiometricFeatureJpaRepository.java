package ai.univs.gate.modules.feature.infrastructure.persistence;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BiometricFeatureJpaRepository extends JpaRepository<BiometricFeature, Long> {

    Optional<BiometricFeature> findByIdAndTypeAndIsDeletedFalse(Long id, FeatureType type);

    /**
     * 행을 잠그고 읽는다 (UG-345). 잠금을 기다린 뒤에는 {@code is_deleted = false} 조건을 다시 보므로, 먼저 커밋된
     * 삭제가 있으면 빈 결과다 — 동시에 온 두 삭제 요청 중 한쪽만 "내가 지웠다" 가 된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BiometricFeature> findForUpdateByIdAndTypeAndIsDeletedFalse(Long id, FeatureType type);

    Optional<BiometricFeature> findByFeatureIdAndProjectIdAndTypeAndIsDeletedFalse(
            String featureId, Long projectId, FeatureType type);

    List<BiometricFeature> findAllByFeatureIdInAndProjectIdAndTypeAndIsDeletedFalse(
            Collection<String> featureIds, Long projectId, FeatureType type);

    long countByProjectIdAndTypeAndIsDeletedFalse(Long projectId, FeatureType type);
}
