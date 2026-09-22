-- UG-282: 대시보드 집계용 (postgresql 쌍둥이 파일의 측정 결과·판단 근거 참고).
--
-- 오라클에서는 CREATE INDEX 가 암묵적 커밋을 유발하므로 이 파일에는 DML 을 두지 않는다
-- (V23 의 전례 참고).
CREATE INDEX idx_match_history_project_match_feature_created
    ON match_history (project_id, match_type, feature_type, created_at);
