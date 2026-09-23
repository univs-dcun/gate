-- UG-282: 이력 보존 정리의 대상 조회 경로 (postgresql 쌍둥이 파일의 배경 참고).
CREATE INDEX idx_match_history_created_at ON match_history (created_at);
CREATE INDEX idx_feature_history_created_at ON feature_history (created_at);
