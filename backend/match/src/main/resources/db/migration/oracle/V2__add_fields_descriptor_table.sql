-- descriptor_type, descriptor_body 필드 추가, descriptor 설명 수정
--
-- UG-296: 예전에는 모든 참조에 UNIVS. 스키마 프리픽스가 붙어 있었다. V1 은 프리픽스 없이
-- 테이블을 만드므로, 접속 계정이 UNIVS 가 아닌 순간 이 파일 첫 줄에서 ORA-00942 로 멈춘다.
-- 개발 계정 이름이 우연히 univs 라 드러나지 않았을 뿐이다. 오라클에서는 이 마이그레이션이
-- 한 번도 실행된 적이 없어(flyway-database-oracle 부재로 부팅 자체가 안 됐다) 체크섬 걱정
-- 없이 지울 수 있었다. 스키마는 접속 계정이 정한다 — SQL 에 적지 않는다.
ALTER TABLE "DESCRIPTOR" ADD DESCRIPTOR_TYPE RAW(8) NULL;

COMMENT ON COLUMN "DESCRIPTOR".DESCRIPTOR_TYPE IS '특징점 타입';

ALTER TABLE "DESCRIPTOR" ADD DESCRIPTOR_BODY RAW(512) NULL;

COMMENT ON COLUMN "DESCRIPTOR".DESCRIPTOR_BODY IS '특징점';

COMMENT ON COLUMN "DESCRIPTOR"."DESCRIPTOR" IS '추출된 레거시 특징점';

-- 기존에 등록된 사용자 특징점 분리 업데이트
UPDATE "DESCRIPTOR" SET descriptor_type = HEXTORAW(SUBSTR(RAWTOHEX(descriptor), 1, 16)), descriptor_body = HEXTORAW(SUBSTR(RAWTOHEX(descriptor), 17, 1024));

-- descriptor_type, descriptor_body NOT NULL
ALTER TABLE "DESCRIPTOR" MODIFY DESCRIPTOR_TYPE RAW(8) NOT NULL;

ALTER TABLE "DESCRIPTOR" MODIFY DESCRIPTOR_BODY RAW(512) NOT NULL;
