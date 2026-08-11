package ai.univs.gate.modules.feature.domain.repository;

import ai.univs.gate.modules.feature.application.input.BiometricFeatureQuery;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import org.springframework.data.domain.Page;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BiometricFeatureRepository {

    BiometricFeature save(BiometricFeature biometricFeature);

    Optional<BiometricFeature> findByIdAndTypeAndIsDeletedFalse(Long id, FeatureType type);

    Optional<BiometricFeature> findByFeatureIdAndProjectIdAndTypeAndIsDeletedFalse(
            String featureId, Long projectId, FeatureType type);

    /**
     * 여러 featureId 를 한 번에 (UG-314).
     *
     * <p>1:N 후보 목록은 최대 100건이라 건별 조회를 돌리면 쿼리가 100번 나간다.
     *
     * <p><b>요청한 것보다 적게 올 수 있다.</b> 하위 서비스에는 있는데 gate 에서 삭제된 특징점이
     * 그렇다. 호출부가 그 차이를 감지해 해당 후보만 빼고 진행한다 — 한 명 때문에 목록 전체가
     * 실패하는 것은 이 API 의 용도에 맞지 않는다.
     */
    List<BiometricFeature> findAllByFeatureIdInAndProjectIdAndTypeAndIsDeletedFalse(
            Collection<String> featureIds, Long projectId, FeatureType type);

    Page<BiometricFeature> findAllByQuery(BiometricFeatureQuery query, Long projectId);

    long countByProjectIdAndTypeAndIsDeletedFalse(Long projectId, FeatureType type);
}
