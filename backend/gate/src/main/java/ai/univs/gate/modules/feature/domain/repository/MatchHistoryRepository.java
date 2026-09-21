package ai.univs.gate.modules.feature.domain.repository;

import ai.univs.gate.modules.feature.domain.entity.MatchHistory;

/**
 * 인증 시도 이력 저장. 조회는 UG-326 부터 {@link ActivityLogRepository} 가 맡는다 — match_history 와
 * feature_history 를 함께 읽어야 하므로 이 리포지토리의 조회 메서드는 제거했다.
 */
public interface MatchHistoryRepository {
    MatchHistory save(MatchHistory matchHistory);
}
