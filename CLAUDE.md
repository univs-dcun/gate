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
- Spring 설정(yml)의 단일 진실은 별도 레포 `univs-dcun/gate-config`의 `main` 브랜치다 (UG-233). 설정 수정은 gate-config 레포에서 main에 직접 커밋 + push 로 완료한다. (PR + Merge 사용 안함)
- 온프레미스(native) 납품 시 config-server가 마운트하는 `/config-repo` 볼륨의 내용물은 gate-config 레포를 클론하여 준비한다. (모노레포에 있던 config-repo 폴더는 UG-233에서 제거됨 — 스냅샷이 낡은 채 납품되는 사고 방지)

## CI/CD 파일 수정 규칙
- Jenkinsfile(파이프라인 로직) 수정 시 dev/stage/master 세 브랜치에 동일 커밋을 cherry-pick 하여 직접 push 한다 (PR 사용 안함). 세 브랜치의 Jenkinsfile은 항상 동일해야 한다.
- 배포 타깃 정보(서버 IP/계정/경로)는 master 브랜치의 `infra/ci-cd/deploy-targets/{dev,stage,master}.env` 가 단일 진실이다 (UG-232). 수정은 master에서 직접 push, 다른 브랜치에 이 폴더를 만들지 않는다.

## 코딩 규칙
- 패키지 구조: controller > usecase (+ service) > repository
- DTO는 record 사용, OpenFeign 에 사용되는 DTO는 class 사용
- 예외처리: GlobalExceptionHandler 중앙 처리
- Backend: docs/backend-coding-conventions.md 참고 (Google Java Style Guide 기반)
- Frontend: docs/frontend-coding-conventions.md 참고 (Airbnb React Style Guide 기반)
