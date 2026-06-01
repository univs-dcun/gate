ALTER TABLE project_settings ADD sdk_enabled NUMBER(1,0) DEFAULT 0 NOT NULL;

COMMENT ON COLUMN project_settings.sdk_enabled IS 'SDK 활성화 여부';
