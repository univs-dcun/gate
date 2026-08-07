-- UG-302 ②: 프로젝트당 활성 API 키는 하나. V23 이 기존 위반을 정리한 뒤 제약을 얹는다.
--
-- 비활성 키는 이력으로 여러 개 남으므로 (project_id, is_active) 전체 유니크는 쓸 수 없다 —
-- 그러면 비활성 키도 프로젝트당 하나가 되어 이력이 쌓이지 않는다. 활성 행만 대상으로 하는
-- 부분 인덱스여야 한다.
--
-- 주의: 이 인덱스는 애플리케이션의 쓰기 **순서**에 의존한다. 재발급이 "기존 비활성화 →
-- 새 키 삽입" 을 하는데 비활성화를 flush 하지 않으면 INSERT 가 먼저 나가 여기에 걸린다.
-- RegenerateApiKeyUseCase 가 그래서 명시적으로 flush 하고, RegenerateApiKeyFlushOrderTest
-- 가 그 순서를 못박는다. 지연 제약으로는 피할 수 없다 — 부분 인덱스는 제약으로 선언할 수
-- 없어 DEFERRABLE 을 붙일 자리가 없다.
CREATE UNIQUE INDEX ux_api_keys_active_project
    ON api_keys (project_id)
 WHERE is_active;
