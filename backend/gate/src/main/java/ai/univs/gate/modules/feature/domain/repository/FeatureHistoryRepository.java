package ai.univs.gate.modules.feature.domain.repository;

import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;

/**
 * 특징점 이력 저장 (UG-325). 조회는 통합 목록(UG-326)이 뷰로 하므로 여기에는 쓰기만 있다.
 */
public interface FeatureHistoryRepository {
    FeatureHistory save(FeatureHistory featureHistory);
}
