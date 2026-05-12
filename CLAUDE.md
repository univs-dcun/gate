# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 서비스 개요
- 서비스명: Gate Service
- 도메인: e-KYC Solution (face, vein of palm authentication)
- 타겟 유저: [은행, 출입문 게이트, B2B]

## 기술 스택
### Backend
- Java 21, Spring Boot 3.x
- Spring Cloud (Eureka, Gateway, Config)
- JPA / PostgreSQL or Oracle (Optional)
- MinIO (파일 스토리지)

### Frontend
- React 18, TypeScript
- React Query (서버 상태)
- Zustand (클라이언트 상태)
- Axios (HTTP 클라이언트)

### Infra
- Docker, GitHub Actions
- 로컬: docker-compose

## 아키텍처 규칙
- 모든 API는 Gateway를 통해 라우팅
- 서비스간 통신은 REST (향후 Kafka 고려)
- 인증은 service-auth에서 JWT 발급, Gateway에서 검증

## API 설계 규칙
- RESTful 원칙 준수
- 응답 포맷: { success, data, message, code }
- 페이징: { content, page, size, totalElements }
- 에러 코드: docs/api-conventions.md 참고

## 코딩 규칙
- 패키지 구조: controller > usecase (+ service) > repository
- DTO는 record 사용, OpenFeign 에 사용되는 DTO는 class 사용
- 예외처리: GlobalExceptionHandler 중앙 처리
- Backend: docs/backend-coding-conventions.md 참고 (Google Java Style Guide 기반)
- Frontend: docs/frontend-coding-conventions.md 참고 (Airbnb React Style Guide 기반)

## Notion 문서화
- 작업 완료 시 Notion MCP로 **기능별 화면 스펙 페이지**를 생성 또는 업데이트
- 날짜 기반 개발일지(개발일지 - YYYY-MM-DD) 형태로 새 페이지를 만들지 않는다
- 기능당 1페이지 원칙: 같은 기능의 후속 작업은 기존 페이지 [작업 이력]에 행 추가
- Notion 페이지 구조: docs/screen-spec-template.md 참고
- 화면 스펙 부모 DB: Gate Service (DB ID: 351e27c5-2739-8041-adfa-e2c1f3e71bed)

## Figma MCP 사용 원칙 — 읽기 전용 (STRICT)

Figma MCP는 **읽기 전용**으로만 사용한다. 어떠한 경우에도 Figma 파일을 생성·수정·삭제하는 도구를 호출하지 않는다.

허용 도구 (읽기만):
- `get_design_context`, `get_screenshot`, `get_metadata`, `get_figjam`
- `get_libraries`, `get_variable_defs`, `search_design_system`, `whoami`

절대 금지 도구 (쓰기):
- `generate_figma_design`, `create_new_file`, `generate_diagram`
- `add_code_connect_map`, `create_design_system_rules`
- `send_code_connect_mappings`, `upload_assets`, `use_figma`

Figma는 기획자가 작성한 산출물이므로, Claude가 직접 수정하면 의도치 않은 덮어쓰기가 발생할 수 있다.

---

## Figma → DevOps 작업 워크플로우

### 시작 조건
기획자가 Figma 화면(스크린샷, URL, 스펙 텍스트)을 전달하면 아래 순서를 따른다.

### [Step 1] API 계약 초안 제시 — 코드 생성 전 필수
- Figma 화면을 분석해 API 목록, Request/Response 구조 초안 정리
- 사용자 확인을 받은 후에만 코드 생성 진행 (확인 없이 코드 생성 금지)

### [Step 2] Backend 생성
생성 순서: Controller → UseCase → Service → Repository → Entity → DTO
- 패키지: `ai.univs.{service}.{layer}`
- DTO는 record 사용 (OpenFeign 연동 시만 class)
- 신규 예외는 GlobalExceptionHandler에 추가

### [Step 3] Frontend 생성
생성 순서: types → api → hooks → components → pages
- 서버 상태: React Query (useQuery, useMutation)
- 클라이언트 상태: Zustand (서버 데이터는 Zustand에 넣지 않음)

### [Step 4] DevOps 업데이트
- 신규 서비스: Dockerfile, docker-compose.yml, infra/k8s manifest 생성
- 기존 서비스 기능 추가: 변경 없으면 생략

### [Step 5] Notion 화면 스펙 페이지 생성 또는 업데이트 — 작업 완료 후 필수
docs/screen-spec-template.md 형식을 사용한다.

- **신규 기능**: 새 페이지 생성. 포함 항목: Figma 원본, 확정 API 계약, 구현 파일, 예상 동작, 작업 이력(최초 구현 행 추가)
- **기존 기능 수정**: 해당 페이지의 [작업 이력] 섹션에 날짜·작업내용·PR 행 추가. 새 페이지 생성 금지
- **인프라/DevOps 작업**: Type=인프라 로 페이지 생성. API 계약·Figma 섹션 대신 변경 내용·영향 범위 기술

### [Step 6] GitHub PR 생성
- 제목 형식: `feat({service}): {화면명} 구현`
- PR 본문에 Notion 화면 스펙 페이지 URL 포함

---

## 버그/이슈 트래킹 규칙

버그가 보고되면 아래 순서로 역추적한다:

1. Notion에서 해당 화면 스펙 페이지 조회
2. [예상 동작] 섹션과 실제 동작 비교
3. 코드를 순서대로 추적: Controller → UseCase → Repository → Entity → DTO
4. Figma 원본과 비교해 설계 의도 재확인
5. 원인 파악 후 수정
6. Notion [이슈 이력] 섹션에 기록:
   `{날짜} | {증상} | {원인} | {수정 파일} | {수정 내용}`

## Figma 화면 변경 시 규칙

1. 변경된 화면 분석 후 영향받는 파일 목록 제시
2. 사용자 확인 후 수정 진행
3. Notion 스펙 페이지 [Figma 원본] 및 [API 계약서] 섹션 업데이트
4. 변경 이력을 [이슈 이력]에 추가: `{날짜} | 기획 변경 | {변경 내용}`
