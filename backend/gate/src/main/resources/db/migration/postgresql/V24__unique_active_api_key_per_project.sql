-- UG-302 ②: 프로젝트당 활성 API 키는 하나. V23 이 기존 위반을 정리한 뒤 제약을 얹는다.
--
-- 비활성 키는 이력으로 여러 개 남으므로 (project_id, is_active) 전체 유니크는 쓸 수 없다 —
-- 그러면 비활성 키도 프로젝트당 하나가 되어 이력이 쌓이지 않는다. 활성 행만 대상으로 하는
-- 부분 인덱스여야 한다.
--
-- 주의: "기존 키를 끄고 새 키를 넣는" 경로를 나중에 추가한다면 이 인덱스와 충돌한다.
-- 비활성화는 더티 마킹일 뿐이고 @GeneratedValue(IDENTITY) 라 save() 가 INSERT 를 즉시
-- 내보내므로, flush 를 끼우지 않으면 그 시점에 기존 행이 아직 활성이라 여기에 걸린다.
-- 지연 제약으로는 피할 수 없다 — 부분 인덱스는 제약으로 선언할 수 없어 DEFERRABLE 을 붙일
-- 자리가 없다. UG-312 에서 그런 경로(키 재발급)를 제거했고,
-- SingleActiveApiKeyGuardTest 가 다시 들어오는 것을 막는다.
CREATE UNIQUE INDEX ux_api_keys_active_project
    ON api_keys (project_id)
 WHERE is_active;
