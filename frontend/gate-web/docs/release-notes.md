# Release Notes — UNIVS Gate Web

---

## v0.0.5 (2026-06-08)

### 🎨 UI 정합
- **설정 — 라이브니스 기능 사용 여부** 카드 디자인 정합 (Figma node 1740:4049)
  - 얼굴/손바닥 패널을 탭 → **좌우 나란히** 배치(각 인증방식 독립 저장)
  - 헤더 아이콘 색상: 얼굴 `#006FFF` / 손바닥 `#8A58FF`
  - 온/오프 토글 크기 축소(50×30 → 44×24), 설명 텍스트 14px

### ⚙️ 개발 환경
- Vite HMR `clientPort: 443` 추가 — HTTPS 리버스 프록시(`dev.univsgate.com`) 뒤에서 HMR WebSocket 연결

---

## v0.0.4 (2026-06-08)

> 신규 디자인 전면 적용 + 신규 백엔드(v3) 연동. 백엔드는 개발 진행 중(WIP)이라 일부 항목은 스펙 변동 가능.

### 🎨 신규 디자인 (얼굴·손바닥 인증 통합)
- 상단 GNB(TopNav) 도입, `DashboardLayout` 전환(좌측 사이드바 → 상단 네비), 언어 선택·개인정보 동의 뱃지
- 로고 신규 교체(단일 SVG, 마크 컬러 `#3B83F6`)
- 대시보드 2열 구성(콘텐츠 + 우측 QR/라이브니스 사이드), 프로젝트 생성 모달/결과 패널, 프로젝트 리스트(인증방식 컬럼)
- 인증방식 배지(`AuthMethodBadge`) — 디자인 시스템 컴포넌트(node 1738:7453) 정합(16px 아이콘, 얼굴/손바닥 색상)

### 🔌 신규 백엔드(v3) 연동
- **인증방식(얼굴/손바닥)**: 로그 상세 컬럼·탭, 대시보드 통계/추이/성공률/일일표가 `featureType(FACE|PALM)` 기준으로 필터 (손바닥은 1:1 인증 항목 자동 제외)
- **특징점 관리**: `/v1/users` → 통합 `/api/v1/feature` 마이그레이션(얼굴/손바닥 필터, modality별 등록·수정·삭제 라우팅). 수동 등록은 얼굴 전용
- **설정 라이브니스**: 얼굴/손바닥 **per-modality 독립 저장**(`livenessSettings[]` 모델, `moduleType + operation`)
- 로그 응답 필드 개명 정합(`featureId`/`description`/`featureImagePath` 등)

### 🌐 인프라/설정
- API/모바일 호스트를 `univsgate.com` 도메인으로 이전 (`api-dev.univsgate.com`, `demo-dev.univsgate.com`)
- Vite dev 서버 `allowedHosts` 허용

### 🧹 정리
- 모듈 테스트 페이지 제거(다른 기능으로 대체 예정), 미사용 `services/sdk.ts`·`services/user.ts` 삭제
- i18n: 영어 모드 미번역 수정, 용어 변경(`테스트 모듈`→`기능`, `Face ID`→`FID`, `1:1 촬영인증`→`1:1 인증`)

---

## v0.0.3 (2026-06-04)

### 🐛 버그 수정

#### 로그인 후 대시보드 데이터 미로딩
- 로그인 직후 대시보드 진입 시 정보가 표시되지 않고 새로고침 필요했던 문제 수정
- 원인: `ProjectContext`가 앱 최상단에 항상 마운트된 상태에서, 로그인 후 navigate 시 프로젝트 쿼리 로딩 중 `selectedProject = undefined` → 대시보드 쿼리 비활성화
- 수정: 로그인 성공 직후 `queryClient.setQueryData(['projects'], contents)`로 캐시 즉시 채워 navigate 후 로딩 없이 `selectedProject` 즉시 확보

#### 로그아웃 시 이전 세션 캐시 잔류
- 로그아웃 후 재로그인 시 이전 세션의 프로젝트/API 키 캐시가 남아 잘못된 데이터가 사용될 수 있었던 문제 수정
- `selected_project_id`, `selected_api_key` localStorage 삭제 + `queryClient.clear()` 추가

### ⚙️ 설정
- `public/config.js`에 `mobileBaseUrl` 런타임 설정 추가

---

## v0.0.2 (2026-05-22)

### ✨ 신규 기능

#### 개인정보 동의 이력 관리
- 설정 페이지 > 개인정보 노출 동의 카드에 **변경 이력** 버튼 추가
- 변경 이력 팝업: `GET /v1/projects/{id}/settings/consent/logs` API 연동
- 타임라인 형태로 동의/거부 이력 표시, 최신 항목에 "현재 설정 값" 배지

#### 개인정보 동의 확인 팝업
- 동의 토글 클릭 시 확인 팝업 표시 (활성화/비활성화 각각 다른 디자인)
- 노출 정보(얼굴 사진, 이름 등) 및 적용 화면 명시

#### 특징점 관리 상세 팝업 (개인정보 동의 시)
- 레코드 클릭 시 우측 상세 패널 표시
- 내용: 얼굴 사진 + FID + 메모 + 등록일시 + 확인 버튼

#### consentSnapshot 처리
- API 응답의 `consentSnapshot` 필드 기반으로 이미지 노출 여부 제어
- 로그 테이블/상세 팝업: 비동의 시 🔒 비공개 표시

---

### 🎨 UI/UX 개선

#### 전체 레이아웃
- `max-width: 1920px` + `mx-auto` 중앙정렬 적용 (Dashboard, Welcome 레이아웃)
- 사이드바 `position: fixed` → flex flow 전환으로 센터링 정상 동작
- body 배경색 흰색(`#ffffff`) 통일

#### 사이드바
- Figma 디자인 반영: default/hover/selected 상태별 스타일 개선
- 선택: 흰색 배경 + 그림자 + `rounded-[8px]` + filled 아이콘
- 호버: 흰색 배경 + `rounded-[12px]`

#### 대시보드 헤더 동의 배지
- "활성/비활성" → **"동의함/미동의"** 배지로 변경
- 방패 아이콘 색상 동기화 (동의함: 주황, 미동의: 다크)

#### 팝업 일관성
- 모든 모달 z-index `z-50` → `z-[var(--z-modal)](400)` 통일
- 오버레이 `bg-black/20` → `bg-[rgba(20,20,20,0.6)] backdrop-blur` 통일

#### 테이블 UI
- 로그 동의 테이블: 가로 스크롤 제거, 이미지 36×36px
- 특징점 관리: 사진 36×36px, INFO → 메모 컬럼명
- 페이지네이션 콤보박스 동작 수정 (FeaturesConsentPage)
- 일일 데이터 통계: 행 패딩 최적화
- 프로젝트 리스트: 아이콘 36×36px, 생성일시 컬럼 추가

---

### 🌐 i18n
- 하드코딩 한국어 50+ 키 i18n 교체 (`건`, `사용함/안함`, `시스템 상세 정보` 등)
- 신규 키: `consent_*`, `logs.*`, `common.private`, `projects.col_created_at` 등
- `동의 안함` → `미동의` 용어 변경

---

### 🐛 버그 수정
- 특징점 관리(동의) 페이지네이션 콤보박스 미동작 수정
- 로그 동의 테이블 가로 스크롤 제거
- 팝업 표시 시 사이드바가 위에 보이는 z-index 문제 수정
- 설정 페이지 동의 배지 아이콘 색상 미반영 수정

---

## v0.0.1 (이전)
- 초기 릴리즈
