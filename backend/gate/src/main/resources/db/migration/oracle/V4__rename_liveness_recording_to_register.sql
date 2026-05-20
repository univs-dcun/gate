-- liveness_recording_enabled 컬럼을 liveness_register_enabled 로 rename
ALTER TABLE project_settings RENAME COLUMN liveness_recording_enabled TO liveness_register_enabled;

COMMENT ON COLUMN project_settings.liveness_register_enabled IS '얼굴 등록 라이브니스 적용 여부';
