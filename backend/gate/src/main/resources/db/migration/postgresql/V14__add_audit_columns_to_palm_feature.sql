-- palm_feature: 누락된 JPA 감사(Audit) 컬럼 추가
ALTER TABLE palm_feature ADD COLUMN IF NOT EXISTS created_by BIGINT DEFAULT NULL;
ALTER TABLE palm_feature ADD COLUMN IF NOT EXISTS updated_by BIGINT DEFAULT NULL;
