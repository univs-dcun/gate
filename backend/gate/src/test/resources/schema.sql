-- UG-328: 두 이력 테이블이 공유하는 사건 시퀀스. 운영은 V29 가 만들고, H2 슬라이스는 ddl-auto 가
-- 시퀀스를 만들지 않으므로 여기서 먼저 만든다 (Spring SQL init 은 Hibernate DDL 보다 앞에 돈다).
CREATE SEQUENCE IF NOT EXISTS activity_seq;
