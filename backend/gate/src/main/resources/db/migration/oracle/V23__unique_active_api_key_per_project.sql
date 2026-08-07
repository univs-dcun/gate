-- UG-302: 프로젝트당 활성 API 키는 하나여야 한다. (postgresql 쌍둥이 파일의 주석 참고)
--
-- 오라클에는 부분 인덱스 문법이 없다. 함수 기반 인덱스로 같은 것을 표현한다 — CASE 가
-- NULL 을 돌려주는 행은 단일 컬럼 인덱스에 실리지 않으므로, 비활성 키는 제약을 받지
-- 않고 활성 키만 프로젝트당 하나로 묶인다.

-- ① 기존 위반 정리. is_active 는 NUMBER(1,0) 이라 1/0 을 쓴다 (오라클에 불리언 리터럴이 없다).
UPDATE api_keys
   SET is_active = 0
 WHERE is_active = 1
   AND api_key_id NOT IN (
       SELECT MAX(api_key_id)
         FROM api_keys
        WHERE is_active = 1
        GROUP BY project_id
   );

-- ② 함수 기반 유니크 인덱스.
CREATE UNIQUE INDEX ux_api_keys_active_project
    ON api_keys (CASE WHEN is_active = 1 THEN project_id END);
