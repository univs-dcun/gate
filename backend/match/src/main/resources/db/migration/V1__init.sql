-- public.branch definition

-- Drop table

-- DROP TABLE public.branch;

CREATE TABLE public.branch (
                               branch_id int8 GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE) NOT NULL,
                               branch_name varchar(255) NOT NULL,
                               created_by varchar(36) NOT NULL,
                               created_at timestamp NOT NULL,
                               modified_by varchar(36) NOT NULL,
                               modified_at timestamp NOT NULL,
                               CONSTRAINT branch_pkey PRIMARY KEY (branch_id)
);


-- public."descriptor" definition

-- Drop table

-- DROP TABLE public."descriptor";

CREATE TABLE public."descriptor" (
                                     descriptor_id int8 GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1 NO CYCLE) NOT NULL,
                                     face_id varchar(36) NOT NULL,
                                     "descriptor" bytea NOT NULL,
                                     descriptor_obtaining_method int4 NULL,
                                     descriptor_version int4 NOT NULL,
                                     descriptor_generation int4 NOT NULL,
                                     branch_id int8 NOT NULL,
                                     created_by varchar(36) NOT NULL,
                                     created_at timestamp NOT NULL,
                                     modified_by varchar(36) NOT NULL,
                                     modified_at timestamp NOT NULL,
                                     CONSTRAINT descriptor_pkey PRIMARY KEY (descriptor_id)
);