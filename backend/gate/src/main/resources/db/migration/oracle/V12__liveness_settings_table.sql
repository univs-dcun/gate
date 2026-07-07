-- project_liveness_settings 테이블 생성
CREATE TABLE project_liveness_settings (
    liveness_setting_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_settings_id NUMBER NOT NULL,
    module_type         VARCHAR2(10) NOT NULL,
    operation           VARCHAR2(20) NOT NULL,
    enabled             NUMBER(1) NOT NULL DEFAULT 0,
    CONSTRAINT uq_project_liveness UNIQUE (project_settings_id, module_type, operation),
    CONSTRAINT fk_project_liveness_settings FOREIGN KEY (project_settings_id)
        REFERENCES project_settings(setting_id)
);

-- 기존 FACE 프로젝트의 liveness 데이터 이전
INSERT INTO project_liveness_settings (project_settings_id, module_type, operation, enabled)
SELECT ps.setting_id, 'FACE', 'REGISTER', ps.liveness_register_enabled
FROM project_settings ps
JOIN projects p ON p.project_id = ps.project_id
WHERE p.project_module_type = 'FACE';

INSERT INTO project_liveness_settings (project_settings_id, module_type, operation, enabled)
SELECT ps.setting_id, 'FACE', 'IDENTIFY', ps.liveness_identifying_enabled
FROM project_settings ps
JOIN projects p ON p.project_id = ps.project_id
WHERE p.project_module_type = 'FACE';

INSERT INTO project_liveness_settings (project_settings_id, module_type, operation, enabled)
SELECT ps.setting_id, 'FACE', 'VERIFY_ID', ps.liveness_verifying_by_id_enabled
FROM project_settings ps
JOIN projects p ON p.project_id = ps.project_id
WHERE p.project_module_type = 'FACE';

INSERT INTO project_liveness_settings (project_settings_id, module_type, operation, enabled)
SELECT ps.setting_id, 'FACE', 'VERIFY_IMAGE', ps.liveness_verifying_by_image_enabled
FROM project_settings ps
JOIN projects p ON p.project_id = ps.project_id
WHERE p.project_module_type = 'FACE';

-- PALM 프로젝트 기본값 insert
INSERT INTO project_liveness_settings (project_settings_id, module_type, operation, enabled)
SELECT ps.setting_id, 'PALM', 'REGISTER', 0
FROM project_settings ps
JOIN projects p ON p.project_id = ps.project_id
WHERE p.project_module_type = 'PALM';

INSERT INTO project_liveness_settings (project_settings_id, module_type, operation, enabled)
SELECT ps.setting_id, 'PALM', 'IDENTIFY', 1
FROM project_settings ps
JOIN projects p ON p.project_id = ps.project_id
WHERE p.project_module_type = 'PALM';

INSERT INTO project_liveness_settings (project_settings_id, module_type, operation, enabled)
SELECT ps.setting_id, 'PALM', 'VERIFY_ID', 0
FROM project_settings ps
JOIN projects p ON p.project_id = ps.project_id
WHERE p.project_module_type = 'PALM';

INSERT INTO project_liveness_settings (project_settings_id, module_type, operation, enabled)
SELECT ps.setting_id, 'PALM', 'VERIFY_IMAGE', 0
FROM project_settings ps
JOIN projects p ON p.project_id = ps.project_id
WHERE p.project_module_type = 'PALM';

-- project_settings: 기존 liveness 컬럼 DROP
ALTER TABLE project_settings DROP COLUMN liveness_register_enabled;
ALTER TABLE project_settings DROP COLUMN liveness_identifying_enabled;
ALTER TABLE project_settings DROP COLUMN liveness_verifying_by_id_enabled;
ALTER TABLE project_settings DROP COLUMN liveness_verifying_by_image_enabled;
