/*

   POSTGRESQL

*/

-- descriptor_type, descriptor_body 필드 추가, descriptor 설명 수정
ALTER TABLE public."descriptor" ADD descriptor_type bytea NULL;

COMMENT ON COLUMN public."descriptor".descriptor_type IS '특징점 타입';

ALTER TABLE public."descriptor" ADD descriptor_body bytea NULL;

COMMENT ON COLUMN public."descriptor".descriptor_body IS '특징점';

COMMENT ON COLUMN public."descriptor"."descriptor" IS '추출된 레거시 특징점';

-- 기존에 등록된 사용자 특징점 분리 업데이트
UPDATE public.descriptor SET descriptor_type = SUBSTRING(descriptor FROM 1 FOR 8), descriptor_body = SUBSTRING(descriptor FROM 9);

-- descriptor_type, descriptor_body NOT NULL
ALTER TABLE public."descriptor" ALTER COLUMN descriptor_type SET NOT NULL;

ALTER TABLE public."descriptor" ALTER COLUMN descriptor_body SET NOT NULL;



/*

   ORACLE

*/

-- descriptor_type, descriptor_body 필드 추가, descriptor 설명 수정
ALTER TABLE UNIVS."DESCRIPTOR" ADD DESCRIPTOR_TYPE RAW(8) NULL;

COMMENT ON COLUMN UNIVS."DESCRIPTOR".DESCRIPTOR_TYPE IS '특징점 타입';

ALTER TABLE UNIVS."DESCRIPTOR" ADD DESCRIPTOR_BODY RAW(512) NULL;

COMMENT ON COLUMN UNIVS."DESCRIPTOR".DESCRIPTOR_BODY IS '특징점';

COMMENT ON COLUMN UNIVS."DESCRIPTOR"."DESCRIPTOR" IS '추출된 레거시 특징점';

-- 기존에 등록된 사용자 특징점 분리 업데이트
UPDATE UNIVS.DESCRIPTOR SET descriptor_type = HEXTORAW(SUBSTR(RAWTOHEX(descriptor), 1, 16)), descriptor_body = HEXTORAW(SUBSTR(RAWTOHEX(descriptor), 17, 1024));

-- descriptor_type, descriptor_body NOT NULL
ALTER TABLE UNIVS."DESCRIPTOR" MODIFY DESCRIPTOR_TYPE RAW(8) NOT NULL;

ALTER TABLE UNIVS."DESCRIPTOR" MODIFY DESCRIPTOR_BODY RAW(512) NOT NULL;
