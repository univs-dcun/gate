-- descriptor_type, descriptor_body 필드 추가, descriptor 설명 수정
ALTER TABLE "descriptor" ADD descriptor_type BYTEA NULL;

COMMENT ON COLUMN "descriptor".descriptor_type IS '특징점 타입';

ALTER TABLE "descriptor" ADD descriptor_body BYTEA NULL;

COMMENT ON COLUMN "descriptor".descriptor_body IS '특징점';

COMMENT ON COLUMN "descriptor"."descriptor" IS '추출된 레거시 특징점';

-- 기존에 등록된 사용자 특징점 분리 업데이트
UPDATE "descriptor" SET descriptor_type = SUBSTRING("descriptor" FROM 1 FOR 8), descriptor_body = SUBSTRING("descriptor" FROM 9);

-- descriptor_type, descriptor_body NOT NULL
ALTER TABLE "descriptor" ALTER COLUMN descriptor_type SET NOT NULL;

ALTER TABLE "descriptor" ALTER COLUMN descriptor_body SET NOT NULL;
