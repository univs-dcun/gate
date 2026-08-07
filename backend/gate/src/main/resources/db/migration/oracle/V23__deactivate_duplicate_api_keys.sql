-- UG-302 ①: 활성 API 키 중복 정리. (postgresql 쌍둥이 파일의 주석에 배경이 있다)
--
-- is_active 는 NUMBER(1,0) 이라 1/0 을 쓴다 — 오라클 SQL 에는 불리언 리터럴이 없다.
-- 인라인 뷰에는 별칭이 필요하다.
UPDATE api_keys
   SET is_active = 0
 WHERE api_key_id IN (
       SELECT api_key_id
         FROM (
              SELECT api_key_id,
                     ROW_NUMBER() OVER (
                         PARTITION BY project_id
                         ORDER BY issued_at DESC, api_key_id DESC
                     ) AS rn
                FROM api_keys
               WHERE is_active = 1
              ) ranked
        WHERE ranked.rn > 1
 );
