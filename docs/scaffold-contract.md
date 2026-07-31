# 스캐폴드 공개 계약 (Scaffold Contract) — 이관됨

> 이 문서의 원본은 **`univs-dcun/msa-scaffold` 레포의 `docs/scaffold-contract.md`** 로 이관되었다 (UG-249, 2026-07-31).
>
> https://github.com/univs-dcun/msa-scaffold/blob/dev/docs/scaffold-contract.md

스캐폴드(discovery-server, config-server, gateway-server, auth-service)와 비즈니스 모듈(gate 등 제품 서비스)
사이의 경계면 계약 — 인증, 공통 응답 포맷, Eureka 네이밍, config, notify 채널, 게이트웨이 설정 소유권 —
은 위 원본만이 기준이다. 경계면을 변경하는 PR은 원본 문서의 버전 갱신을 동반해야 한다.

스캐폴드 서비스의 소스도 같은 레포에 있다 (모노레포의 `backend/{auth,config,discovery,gateway}`는 UG-249에서 제거,
커밋 이력은 git filter-repo로 이관되어 msa-scaffold에 보존됨).
