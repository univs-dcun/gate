-- UG-340: 같은 브랜치에 같은 face_id 가 두 번 생기지 않게 한다. 사유는 postgresql/V4 주석과 같다.
--
-- 이미 중복이 있으면 ORA-02299 로 실패한다(의도). Oracle 은 DDL 이 롤백되지 않아 flyway_schema_history 에
-- 실패 행이 남는다 — 중복을 정리한 뒤 그 행을 지우거나 flyway repair 를 해야 다시 기동한다. 그래서 적용
-- 전 확인이 필수다: docs/onpremise-oracle-setup.md 「match V4·V5 적용 전 중복 확인」.
ALTER TABLE "DESCRIPTOR" ADD CONSTRAINT uk_descriptor_branch_face UNIQUE (branch_id, face_id);
