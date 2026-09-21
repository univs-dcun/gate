-- UG-326: 로그 상세 정렬용 (postgresql 쌍둥이 파일의 주석 참고).
CREATE INDEX idx_match_history_project_created ON match_history(project_id, created_at);
