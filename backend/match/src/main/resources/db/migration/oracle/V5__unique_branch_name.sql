-- UG-340: 같은 이름의 브랜치가 두 번 생기지 않게 한다. 사유는 postgresql/V5, 실패 시 처리는 oracle/V4 주석과 같다.
ALTER TABLE BRANCH ADD CONSTRAINT uk_branch_branch_name UNIQUE (branch_name);
