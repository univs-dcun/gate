# CLAUDE.md

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

## 브랜치 규칙
- **모든 브랜치는 `dev` 브랜치 기반으로 생성한다** (`git checkout dev && git checkout -b {브랜치명}`)
- 브랜치 네이밍: docs/branch-naming-conventions.md 참고
- PR 대상 브랜치: `dev`

## Spring 환경 파일 수정 규칙
- config-repo 폴더 하위에 파일 수정 요청이 발생한 경우 반드시 master branch 에서 작업하고 Github 에 push 로 작업을 완료한다. (PR + Merge 사용 안함)

## 코딩 규칙
- 패키지 구조: controller > usecase (+ service) > repository
- DTO는 record 사용, OpenFeign 에 사용되는 DTO는 class 사용
- 예외처리: GlobalExceptionHandler 중앙 처리
- Backend: docs/backend-coding-conventions.md 참고 (Google Java Style Guide 기반)
- Frontend: docs/frontend-coding-conventions.md 참고 (Airbnb React Style Guide 기반)
