# Changelog

모든 변경 이력을 버전별로 기록합니다.

---

## v2.0.1 (2026-05-18)

### Backend — gate-service

#### 버그 수정
- 사용자 수정 API(`PUT /api/v1/users`)에서 `faceId` 클라이언트 직접 수정 불가 처리
  - `UpdateUserRequestDTO`에서 `faceId` 필드 제거
  - `UpdateFeignRequestDTO` 생성 시 `input.faceId()` → `user.getFaceId()` 로 교체
  - `User.updateUserInfo()`에서 `faceId` 파라미터 제거

### on-premise 적용 가이드

#### 변경된 서비스 이미지
- gate-service 재빌드 및 이미지 업데이트 필요

#### DB 마이그레이션
- 없음

---

## v2.0.0 (2026-05-18)

### Backend — gate-service

#### 기능 추가
- 대시보드 기간 필터(WEEK / MONTH / YEAR) 추가 — `/dashboard/summary`, `/dashboard/ratios`
- 대시보드 데모 실행 QR 코드 조회 API 추가 — `GET /api/v1/dashboard/demo-qr`
- 대시보드 summary `UsageSummary`에 `periodCount` / `totalCount` 분리
- 프로젝트 목록 `countVerify` → `countVerifyById` / `countVerifyByImage` 분리
- 1:1 라이브니스 적용 설정 분리 — `livenessVerifyingByIdEnabled` / `livenessVerifyingByImageEnabled`
- `/verify/image` 엔드포인트에 `checkAvailabilityModules` 검사 추가
- 동의 여부에 따른 이미지 경로 조건부 응답 적용
- User에 `username` 필드 추가 및 관련 API 응답 반영
- 개인정보 동의 기반 이미지 저장 및 매칭 응답에 이미지 경로 반환
- Demo 사용자 목록 조회 API 추가 (API Key 기반)
- 이미지 파일 조회 API 추가 — `GET /api/v1/file`
- PostgreSQL / Oracle 다중 DB 지원 추가

#### 버그 수정
- `DATE_FORMAT` → `TO_CHAR` 교체 (PostgreSQL / Oracle 호환)
- `LocalDateTime` 응답 포맷 `yyyy-MM-dd HH:mm:ss` 통일
- 대시보드 응답 `UsageSummary` 필드 구조 복원
- `consentEnabled` 누락 및 `UserService` username 파라미터 추가

#### DB 마이그레이션 (Flyway 자동 적용)
- `V3__split_liveness_verifying_field` — `liveness_verifying_enabled` 컬럼을 `liveness_verifying_by_id_enabled` / `liveness_verifying_by_image_enabled` 로 분리

#### 변경된 환경변수
| 변수명 | 설명 | 비고 |
|---|---|---|
| `GATE_DEMO_URL` | 데모 QR에 사용될 URL | 신규 추가 |

### Backend — auth-service

#### 기능 추가
- 온프레미스 관리자 계정 초기화 API 추가
- PostgreSQL / Oracle 다중 DB 지원 추가

### Frontend — demo-web

#### 추가
- `frontend/demo-web` — face-auth-demo 프로젝트 git submodule로 등록
- `frontend/demo-web-config/` — SSL 및 배포 커스텀 파일 관리
  - `Dockerfile` (multi-stage, linux/amd64)
  - `server-https.js` (HTTPS 지원 래퍼)
  - `docker-entrypoint.sh` (런타임 환경변수 주입)
  - `docker-compose.yml` (SSL 볼륨 마운트 포함)
- `frontend/build-demo-web.sh` — submodule 업데이트 → 커스텀 파일 적용 → 빌드/push 자동화 스크립트

#### SSL 설정
- 와일드카드 인증서 (`*.univs.ai`) 런타임 마운트 방식 적용
- `SSL_CERT_PATH` / `SSL_KEY_PATH` 환경변수로 인증서 경로 주입

### Infra / 공통

- billing 서비스 완전 제거 및 온프레미스 전환
- Config Server git 소스 Bitbucket → GitHub 변경
- `docs/branch-naming-conventions.md` 추가
- `setup.sh` — 레포 초기 세팅 스크립트 추가 (submodule 초기화 포함)

---

### on-premise 적용 가이드

#### 1. 신규 환경변수 추가 (`.env`)
```env
GATE_DEMO_URL=https://<서버도메인 또는 IP>:<포트>
```

#### 2. demo-web 서비스 추가 (`docker-compose.yml`)
```yaml
demo-web:
  container_name: demo-web
  image: ${DOCKHUB_URL}/demo-web:${DEMO_WEB_VERSION}
  ports:
    - "3000:3000"
  environment:
    - UNIVS_API_BASE_URL=http://gateway-server:8080
    - SSL_CERT_PATH=/etc/ssl/univs/univs.crt
    - SSL_KEY_PATH=/etc/ssl/univs/univs.key
  volumes:
    - /etc/ssl/univs:/etc/ssl/univs:ro
  networks:
    - univs-network
  restart: unless-stopped
```

#### 3. SSL 인증서 서버에 배치
```bash
sudo mkdir -p /etc/ssl/univs
sudo cp _wildcard_.univs.ai.all.crt.pem /etc/ssl/univs/univs.crt
sudo cp _wildcard_.univs.ai.key.pem     /etc/ssl/univs/univs.key
```

#### 4. 이미지 pull 및 재시작
```bash
docker compose pull && docker compose up -d
```

---

## v1.0.0

- 초기 서비스 구성 (auth, gate, face, match, config, discovery, gateway)
- Jenkins CI/CD 파이프라인 구성
- 모노레포 구조 확립
