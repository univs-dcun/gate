-- UG-344: 프로젝트당 웹훅 설정 1행 (postgresql 쌍둥이 파일 참고).
ALTER TABLE webhook_configs ADD CONSTRAINT uq_webhook_configs_project_id UNIQUE (project_id);
