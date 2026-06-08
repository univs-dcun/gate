-- 누락된 FACE liveness 설정 backfill
INSERT INTO project_liveness_settings (project_settings_id, module_type, operation, enabled)
SELECT ps.setting_id, 'FACE', 'REGISTER', FALSE
FROM project_settings ps
WHERE NOT EXISTS (
    SELECT 1 FROM project_liveness_settings
    WHERE project_settings_id = ps.setting_id AND module_type = 'FACE' AND operation = 'REGISTER'
);

INSERT INTO project_liveness_settings (project_settings_id, module_type, operation, enabled)
SELECT ps.setting_id, 'FACE', 'IDENTIFY', TRUE
FROM project_settings ps
WHERE NOT EXISTS (
    SELECT 1 FROM project_liveness_settings
    WHERE project_settings_id = ps.setting_id AND module_type = 'FACE' AND operation = 'IDENTIFY'
);

INSERT INTO project_liveness_settings (project_settings_id, module_type, operation, enabled)
SELECT ps.setting_id, 'FACE', 'VERIFY_ID', TRUE
FROM project_settings ps
WHERE NOT EXISTS (
    SELECT 1 FROM project_liveness_settings
    WHERE project_settings_id = ps.setting_id AND module_type = 'FACE' AND operation = 'VERIFY_ID'
);

INSERT INTO project_liveness_settings (project_settings_id, module_type, operation, enabled)
SELECT ps.setting_id, 'FACE', 'VERIFY_IMAGE', TRUE
FROM project_settings ps
WHERE NOT EXISTS (
    SELECT 1 FROM project_liveness_settings
    WHERE project_settings_id = ps.setting_id AND module_type = 'FACE' AND operation = 'VERIFY_IMAGE'
);

-- 누락된 PALM liveness 설정 backfill
INSERT INTO project_liveness_settings (project_settings_id, module_type, operation, enabled)
SELECT ps.setting_id, 'PALM', 'REGISTER', FALSE
FROM project_settings ps
WHERE NOT EXISTS (
    SELECT 1 FROM project_liveness_settings
    WHERE project_settings_id = ps.setting_id AND module_type = 'PALM' AND operation = 'REGISTER'
);

INSERT INTO project_liveness_settings (project_settings_id, module_type, operation, enabled)
SELECT ps.setting_id, 'PALM', 'IDENTIFY', TRUE
FROM project_settings ps
WHERE NOT EXISTS (
    SELECT 1 FROM project_liveness_settings
    WHERE project_settings_id = ps.setting_id AND module_type = 'PALM' AND operation = 'IDENTIFY'
);

INSERT INTO project_liveness_settings (project_settings_id, module_type, operation, enabled)
SELECT ps.setting_id, 'PALM', 'VERIFY_ID', FALSE
FROM project_settings ps
WHERE NOT EXISTS (
    SELECT 1 FROM project_liveness_settings
    WHERE project_settings_id = ps.setting_id AND module_type = 'PALM' AND operation = 'VERIFY_ID'
);

INSERT INTO project_liveness_settings (project_settings_id, module_type, operation, enabled)
SELECT ps.setting_id, 'PALM', 'VERIFY_IMAGE', FALSE
FROM project_settings ps
WHERE NOT EXISTS (
    SELECT 1 FROM project_liveness_settings
    WHERE project_settings_id = ps.setting_id AND module_type = 'PALM' AND operation = 'VERIFY_IMAGE'
);
