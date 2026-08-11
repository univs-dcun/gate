-- UG-302 ②: 프로젝트당 활성 API 키는 하나. (postgresql 쌍둥이 파일의 주석 참고)
--
-- 오라클에는 부분 인덱스 문법이 없다. 함수 기반 인덱스로 같은 것을 표현한다 — CASE 가
-- NULL 을 돌려주는 행은 단일 컬럼 인덱스에 실리지 않으므로, 비활성 키는 제약을 받지 않고
-- 활성 키만 프로젝트당 하나로 묶인다.
CREATE UNIQUE INDEX ux_api_keys_active_project
    ON api_keys (CASE WHEN is_active = 1 THEN project_id END);
