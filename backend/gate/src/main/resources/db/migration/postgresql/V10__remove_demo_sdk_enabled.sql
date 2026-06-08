-- project_settings: demo_enabled, sdk_enabled 컬럼 제거
ALTER TABLE project_settings DROP COLUMN demo_enabled;
ALTER TABLE project_settings DROP COLUMN sdk_enabled;

-- webhook_configs: sdk_enabled 컬럼 제거
ALTER TABLE webhook_configs DROP COLUMN sdk_enabled;
