ALTER TABLE project_settings ADD sdk_enabled BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN project_settings.sdk_enabled IS 'SDK 활성화 여부';
