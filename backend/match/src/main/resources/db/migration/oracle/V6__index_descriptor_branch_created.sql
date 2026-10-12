-- UG-374: 같은 얼굴 동시 등록의 커밋 뒤 재검사용 인덱스. 사유는 postgresql/V6 주석과 같다.
-- ONLINE 은 쓰지 않는다 — Enterprise Edition 기능이라 납품 환경에 따라 실패한다.
CREATE INDEX idx_descriptor_branch_created ON "DESCRIPTOR" (branch_id, created_at);
