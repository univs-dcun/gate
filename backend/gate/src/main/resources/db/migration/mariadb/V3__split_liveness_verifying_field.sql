-- liveness_verifying_enabled 컬럼을 liveness_verifying_by_id_enabled 로 rename
ALTER TABLE project_settings
    CHANGE COLUMN liveness_verifying_enabled
        liveness_verifying_by_id_enabled TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '1:1 촬영 인증(/verify/id) 라이브니스 적용 여부';

-- liveness_verifying_by_image_enabled 신규 추가 (기존 데이터는 활성화 상태로 기본값 설정)
ALTER TABLE project_settings
    ADD COLUMN liveness_verifying_by_image_enabled TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '1:1 사진 인증(/verify/image) 라이브니스 적용 여부';
