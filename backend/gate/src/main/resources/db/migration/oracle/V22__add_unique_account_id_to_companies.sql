-- UG-243: 회사 정보 lazy 생성의 동시 요청 경합 방지 — 계정당 회사 1행 보장
ALTER TABLE companies ADD CONSTRAINT uq_companies_account_id UNIQUE (account_id);
