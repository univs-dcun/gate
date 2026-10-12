-- UG-374: 같은 얼굴 동시 등록의 커밋 뒤 재검사가 "같은 브랜치의 최근 특징점" 만 읽게 한다.
--
-- 등록은 1:N 중복 검사 → INSERT → 커밋이다. READ COMMITTED 에서는 동시에 진행 중인 다른 등록의 INSERT 가 보이지
-- 않아, 같은 얼굴 요청들이 모두 "중복 없음" 을 보고 각자 저장했다. 이제 커밋 직후 같은 브랜치의 최근 특징점만
-- 다시 비교한다(RecentDuplicateCheck). 그 조회가 브랜치 전체를 훑지 않으려면 (branch_id, created_at) 인덱스가 필요하다 —
-- 기존 인덱스는 PK 와 uk_descriptor_branch_face(branch_id, face_id) 뿐이다.
--
-- CONCURRENTLY 를 쓰지 않는다. Flyway 가 트랜잭션 잠금을 쥔 채 실행하면 CONCURRENTLY 는 그 트랜잭션이 끝나기를
-- 기다려 멈춘다. 대신 만드는 동안 descriptor 쓰기가 막힌다 — 100만 행에서 수 초. 기동 시 마이그레이션이라 트래픽 전이다.
CREATE INDEX idx_descriptor_branch_created ON "descriptor" (branch_id, created_at);
