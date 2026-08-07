-- UG-302: 프로젝트당 활성 API 키는 하나여야 한다.
--
-- 코드베이스 곳곳이 이것을 가정하지만 그것을 보장하는 제약이 없었다. api_keys 에는
-- UNIQUE (api_key) 뿐이고, RegenerateApiKeyUseCase 가 잠금 없이 "기존 비활성화 → 새 키
-- 삽입" 을 해서 동시 호출이면 활성 2개가 남았다. 그 상태가 되면 프로젝트 상세 조회와
-- 키 재발급이 둘 다 500 이 됐다 — 재발급이 막히면 그 프로젝트는 스스로 빠져나올 수단을
-- 잃는다.
--
-- 코드 쪽은 이미 닫혔다 (프로젝트 행 PESSIMISTIC_WRITE 로 신규 발생 차단, 재발급이
-- 활성 키를 전부 끄는 것으로 기존 상태 치유). 이 마이그레이션은 그 위에 스키마 제약을
-- 얹어 어떤 경로로도 다시 생기지 않게 한다.

-- ① 먼저 기존 위반을 정리한다.
--
-- 인덱스만 만들면 위반 행이 있는 환경에서 마이그레이션이 실패하고, 그 환경의 스키마는
-- 여기서 멈춘다. 실제로 dev 에 한 건 있었고(project_id=53), 온프레미스 납품처 DB 는
-- 들여다볼 수 없어 같은 일이 설치 현장에서 날 수 있다. 납품이 막히는 쪽이 더 나쁘다.
--
-- 남기는 기준은 api_key_id 최대값이다. IDENTITY 라 삽입 순서와 같고, 조회 경로
-- (findLatestActiveByProjectId) 가 이미 issued_at DESC, id DESC 로 같은 행을 고르고 있다.
-- 즉 대시보드가 "그 프로젝트의 키" 로 보여 주던 것과 일치한다 — 정리는 DB 를 화면에
-- 맞추는 쪽이다.
UPDATE api_keys
   SET is_active = FALSE
 WHERE is_active = TRUE
   AND api_key_id NOT IN (
       SELECT MAX(api_key_id)
         FROM api_keys
        WHERE is_active = TRUE
        GROUP BY project_id
   );

-- ② 부분 유니크 인덱스.
--
-- 비활성 키는 이력으로 여러 개 남으므로 (project_id, is_active) 전체 유니크는 쓸 수 없다.
-- 활성 행만 대상으로 하는 부분 인덱스여야 한다.
CREATE UNIQUE INDEX ux_api_keys_active_project
    ON api_keys (project_id)
 WHERE is_active;
