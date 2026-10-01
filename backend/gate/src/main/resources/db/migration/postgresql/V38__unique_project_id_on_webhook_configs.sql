-- UG-344: 프로젝트당 웹훅 설정 1행 (V37 이 중복을 지운 뒤에 건다). 앱은 저장 때 프로젝트 행을 잠가 동시 첫 저장을
-- 직렬화하지만, 그 한 겹에 기대지 않고 DB 가 마지막으로 막는다.
ALTER TABLE webhook_configs ADD CONSTRAINT uq_webhook_configs_project_id UNIQUE (project_id);
