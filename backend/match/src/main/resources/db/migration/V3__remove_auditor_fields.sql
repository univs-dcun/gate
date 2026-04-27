ALTER TABLE public.branch DROP COLUMN created_by;
ALTER TABLE public.branch DROP COLUMN modified_by;

ALTER TABLE public."descriptor" DROP COLUMN created_by;
ALTER TABLE public."descriptor" DROP COLUMN modified_by;
