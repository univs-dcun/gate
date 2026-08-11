-- UG-302 ①: 프로젝트당 활성 API 키가 여러 개인 상태를 정리한다. 인덱스는 V24 가 만든다.
--
-- 왜 인덱스와 같은 파일에 두지 않는가 (반박 리뷰 지적).
-- 오라클은 CREATE INDEX 가 암묵적 커밋을 유발한다. 한 파일에 두면 UPDATE 가 커밋된 뒤
-- 인덱스 생성이 실패할 수 있고, 그러면 flyway_schema_history 에 실패 행이 남아 이후 매
-- 기동이 "Detected failed migration" 으로 죽는다 (validate-on-migrate 기본값 true,
-- compose 는 restart: unless-stopped → 크래시 루프). 버전을 나누면 정리는 성공으로
-- 기록되고 재실행 시 인덱스만 다시 시도한다.
--
-- 왜 정리를 먼저 하는가.
-- 인덱스만 만들면 위반 행이 있는 환경에서 생성이 실패하고 그 환경의 스키마가 멈춘다.
-- 실제로 dev 에 한 건 있었다(project_id=53). 온프레미스 납품처 DB 는 들여다볼 수 없어
-- 같은 일이 설치 현장에서 날 수 있고, 납품이 막히는 쪽이 더 나쁘다.
--
-- 남기는 기준: issued_at DESC, api_key_id DESC 의 첫 행.
-- 조회 경로 ApiKeyRepository.findLatestActiveByProjectId 와 **같은 정렬**이다. 즉 대시보드가
-- "그 프로젝트의 키" 로 보여 주던 행을 남긴다 — 정리는 DB 를 화면에 맞추는 쪽이다.
--
-- 초판은 MAX(api_key_id) 를 썼는데 그 둘이 항상 같은 행이 아니다(반박 리뷰가 반례를
-- 실측했다). issued_at 은 DB 기본값이 아니라 애플리케이션이 넣는다 — now() 를 계산한 뒤
-- INSERT 하기까지 사이에 다른 스레드가 끼어들면 issued_at 이 더 큰데 id 가 더 작은 행이
-- 생긴다. 활성 2개 상태 자체가 바로 그 동시 호출로 생긴 것이므로 남 얘기가 아니다.
UPDATE api_keys
   SET is_active = FALSE
 WHERE api_key_id IN (
       SELECT api_key_id
         FROM (
              SELECT api_key_id,
                     ROW_NUMBER() OVER (
                         PARTITION BY project_id
                         ORDER BY issued_at DESC, api_key_id DESC
                     ) AS rn
                FROM api_keys
               WHERE is_active = TRUE
              ) ranked
        WHERE ranked.rn > 1
 );
