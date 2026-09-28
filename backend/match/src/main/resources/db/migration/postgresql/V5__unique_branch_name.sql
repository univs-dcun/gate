-- UG-340: 같은 이름의 브랜치가 두 번 생기지 않게 한다.
--
-- 새 프로젝트의 첫 등록 두 건이 동시에 오면 둘 다 브랜치가 없다고 보고 각자 만든다. 그 뒤로는
-- findByBranchName 이 결과 둘을 받아 예외를 던진다 — 그 프로젝트의 등록·인증·삭제가 전부 멈춘다.
-- 경합에서 진 쪽은 등록이 서버 오류로 끝나고, 재시도하면 이미 있는 브랜치로 정상 경로를 탄다.
--
-- 이미 중복이 있으면 실패한다(의도). 적용 전 확인은 V4 주석과 같은 문서.
ALTER TABLE branch ADD CONSTRAINT uk_branch_branch_name UNIQUE (branch_name);
