-- users → face_feature 테이블 rename
ALTER TABLE users RENAME TO face_feature;

-- PK 컬럼명 rename
ALTER TABLE face_feature RENAME COLUMN user_id TO face_feature_id;

-- external_key 컬럼 추가 (동일 인물의 face/palm 연결용 선택적 키)
ALTER TABLE face_feature ADD COLUMN external_key VARCHAR(255) DEFAULT NULL;

-- AI 서비스 식별자·이미지 경로 컬럼 rename
ALTER TABLE face_feature RENAME COLUMN face_id TO feature_id;
ALTER TABLE face_feature RENAME COLUMN face_image_path TO feature_image_path;

COMMENT ON TABLE  face_feature                     IS '얼굴 인증 매체';
COMMENT ON COLUMN face_feature.face_feature_id     IS '얼굴 인증 매체 식별 번호';
COMMENT ON COLUMN face_feature.project_id          IS '프로젝트 식별 번호';
COMMENT ON COLUMN face_feature.feature_id          IS '얼굴 AI 서비스 식별자';
COMMENT ON COLUMN face_feature.feature_image_path  IS '얼굴 이미지 경로';
COMMENT ON COLUMN face_feature.description         IS '인증 매체 설명';
COMMENT ON COLUMN face_feature.username            IS '인증 매체 이름';
COMMENT ON COLUMN face_feature.is_deleted          IS '삭제 여부';
COMMENT ON COLUMN face_feature.transaction_uuid    IS '등록 요청 키';
COMMENT ON COLUMN face_feature.external_key        IS '동일 인물의 face/palm 연결용 외부 키';

-- match_history: user_id → face_feature_id rename
ALTER TABLE match_history RENAME COLUMN user_id TO face_feature_id;

-- match_history: feature_type 컬럼 추가 (기존 데이터는 FACE로 backfill)
ALTER TABLE match_history ADD COLUMN feature_type VARCHAR(10) DEFAULT 'FACE';
UPDATE match_history SET feature_type = 'FACE';
ALTER TABLE match_history ALTER COLUMN feature_type SET NOT NULL;

COMMENT ON COLUMN match_history.face_feature_id   IS '매치 성공 시 인증 매체 식별 번호';
COMMENT ON COLUMN match_history.feature_type      IS '인증 매체 타입 (FACE, PALM)';
