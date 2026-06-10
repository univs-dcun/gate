# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
npm run dev       # 개발 서버 실행 (http://localhost:5173)
npm run build     # 프로덕션 빌드 (dist/ 출력)
npm run preview   # 빌드 결과물 로컬 미리보기
npm run lint      # ESLint 실행
```

## Architecture

**Tech Stack**: Vite + React 19 + TypeScript + Tailwind CSS v4

**Key Libraries**:
- `react-router-dom` v7 — 클라이언트 사이드 라우팅
- `@tanstack/react-query` — 서버 상태 관리 및 캐싱
- `axios` — HTTP 클라이언트 (`src/services/http.ts`에 인터셉터 포함)
- `recharts` — 차트 라이브러리 (LineChart, PieChart)
- `react-i18next` — 다국어 지원 (ko / en)

### Directory Structure

```
src/
├── components/
│   ├── ui/          # 원자 단위 디자인 시스템 컴포넌트
│   ├── layout/      # 레이아웃 컴포넌트
│   └── common/      # 도메인 공통 컴포넌트
├── contexts/        # React Context (ProjectContext 등)
├── pages/           # 라우트별 페이지 컴포넌트
├── hooks/           # 커스텀 훅
├── services/        # API 호출 함수 (http.ts 기반)
├── types/           # 공유 TypeScript 타입 (index.ts)
├── utils/           # 순수 유틸리티 함수
├── i18n/
│   ├── index.ts            # i18n 초기화 (main.tsx에서 import)
│   └── locales/
│       ├── ko.json         # 한국어
│       └── en.json         # 영어
├── styles/
│   └── tokens.css   # 디자인 토큰 (CSS 변수) — Figma 스펙 기반
└── assets/
```

---

## Pages & Routing

`src/App.tsx`에서 `BrowserRouter` + `Routes` 설정.

| 경로 | 컴포넌트 | 인증 | 설명 |
|---|---|---|---|
| `/` | `HomePage` | 불필요 | 비인증 랜딩 |
| `/login` | `LoginPage` | 불필요 | 로그인 |
| `/signup` | `SignupPage` | 불필요 | 회원가입 |
| `/verify-email` | `VerifyEmailPage` | 불필요 | 이메일 인증 |
| `/set-password` | `SetPasswordPage` | 불필요 | 비밀번호 설정 |
| `/welcome` | `ProjectWelcomePage` | 필요 | 프로젝트 최초 생성 안내 |
| `/projects` | `ProjectListPage` | 필요 | 프로젝트 목록 |
| `/dashboard` | `DashboardPage` | 필요 | 대시보드 (AppLayout) |
| `/dashboard/module` | `ModuleTestPage` | 필요 | SDK 모듈 테스트 |
| `/dashboard/logs` | `LogDetailPage` | 필요 | 로그 상세 조회 |
| `/dashboard/features` | `FeaturesPage` | 필요 | 특징점 관리 |
| `/dashboard/support` | `DevSupportPage` | 필요 | 개발자 지원 |
| `/dashboard/settings` | `SettingsPage` | 필요 | 설정 |
| `/m/test/:type` | `MobileTestPage` | 불필요 | 모바일 얼굴 인식 테스트 |
| `/m/network-error` | `MobileNetworkErrorPage` | 불필요 | 모바일 네트워크 오류 |
| `/network-error` | `NetworkErrorPage` | 불필요 | 네트워크 오류 |

새 페이지 추가 시: `src/pages/`에 컴포넌트 생성 → `App.tsx`에 `<Route>` 추가.

---

## API Integration

### HTTP 클라이언트 (`src/services/http.ts`)

axios 인스턴스 (`baseURL: VITE_API_BASE_URL || '/api'`):

**요청 인터셉터** — 모든 요청에 자동 부착:
- `Authorization: Bearer {access_token}` — localStorage의 토큰
- `X-Api-Key: {currentApiKey}` — ProjectContext에서 선택된 프로젝트 API 키
- `Accept-Language: ko|en` — localStorage의 `lang` 값
- `Accept-TimeZone` — 브라우저 타임존

**Public Path 예외** (`/v1/auth`, `/v1/login`):
- Authorization, X-Api-Key 헤더 미첨부

**응답 인터셉터**:
- 네트워크 오류 → `/network-error` 또는 `/m/network-error` 리다이렉트
- 401 → `access_token` 삭제 후 `/login` 리다이렉트

### 서비스 파일

| 파일 | 역할 |
|---|---|
| `src/services/http.ts` | axios 인스턴스 + 인터셉터 |
| `src/services/auth.ts` | 로그인/회원가입/이메일인증 |
| `src/services/project.ts` | 프로젝트 CRUD |
| `src/services/user.ts` | 사용자 정보 |
| `src/services/log.ts` | 로그 조회 |
| `src/services/sdk.ts` | SDK QR 발급 API |
| `src/services/demo.ts` | 모바일 데모 API (별도 mobileClient 인스턴스) |

### SDK API 엔드포인트

| 모듈 | 메서드 | 엔드포인트 |
|---|---|---|
| register | GET | `/v1/sdk/user/qr` |
| verify | GET | `/v1/sdk/verify/qr/{faceId}` |
| match | GET | `/v1/sdk/identify/qr` |
| liveness | GET | `/v1/sdk/liveness/qr` |

### Demo API

`src/services/demo.ts` — `mobileClient` 별도 인스턴스:
- 엔드포인트: `/v1/demo/*` (모바일 얼굴 인식 처리)
- `Accept-Language` 인터셉터 포함
- Demo QR URL은 직접 구성: `/m/test/:type?apikey=...&fid=...`

환경변수는 `.env.local` 파일에 설정 (`.env.example` 참고).

---

## State Management

### ProjectContext (`src/contexts/ProjectContext.tsx`)

선택된 프로젝트를 전역 관리. `ProjectProvider`로 앱 전체 래핑.

- 선택 프로젝트 ID를 `localStorage('selected_project_id')`에 영속 저장
- 페이지 새로고침 시 저장된 ID 복원, 목록에 없으면 첫 번째 프로젝트로 폴백
- 선택 프로젝트의 `apiKey`를 `setCurrentApiKey()`로 인터셉터에 즉시 동기화

```tsx
const { projects, selectedId, setSelectedId, selectedProject, isLoading } = useProjectContext();
```

---

## i18n

`src/i18n/index.ts`에서 초기화, `main.tsx`에서 import.

- 언어 저장: `localStorage('lang')` — `'ko'` 또는 `'en'`
- 언어 변경: `changeLanguage('ko' | 'en')` 함수 사용

**규칙**:
- 화면에 노출되는 모든 텍스트는 반드시 `ko.json` + `en.json`에 키 추가
- 번역 키 네임스페이스 구조: `섹션.키` (예: `common.save`, `mobile_test.retake`)
- `common.*` — 범용 공통 레이블 (저장, 취소, 유사도, 메모 등)
- `logs.*` / `mobile_test.*` 등 — 도메인별 전용 키
- `t('common.score')` 같은 호출이 리터럴로 나오면 **`common` 섹션에 해당 키가 없는 것** — `ko.json`/`en.json`의 `"common"` 블록에 추가할 것

---

## Mobile Test Page (`/m/test/:type`)

URL 파라미터: `type(register|verify|match|liveness)`, `apikey`, `fid`

**요청 ID 생성 규칙**:
```ts
// 페이지 로드 시 8자리 세션 prefix 고정 (장비 식별용)
const sessionPrefix = generateSessionPrefix(); // crypto.getRandomValues 기반 hex 8자

// 매 촬영마다 새 requestId 생성 (prefix 유지)
const requestId = generateRequestId(sessionPrefix); // prefix + UUID v4 나머지
```

**결과 화면 컴포넌트**:
- `RegisterResultScreen` — 등록 성공 (FaceId + 요청ID 표시)
- `VerifyResultScreen` — 1:1 검증 결과 (성공 시만 정보카드)
- `MatchResultScreen` — 1:N 매칭 결과 (성공 시만 정보카드)
- `LivenessResultScreen` — 라이브니스 결과
- FID / 요청ID: `font-mono break-all leading-snug` — 전체 값 표시 (truncate 금지)
- 실패 시 설명: `failureReason` 우선, 없으면 기본 메시지 사용

**현재 상태**: Demo 모드만 노출 (`testMode: TestMode = 'demo'`), SDK 탭 숨김 처리

---

## Design System (Figma 기반)

**폰트**: Pretendard Variable (`index.html`에 CDN으로 로드)

### 디자인 토큰 ([src/styles/tokens.css](src/styles/tokens.css))

Figma 색상 네이밍 그대로 CSS 변수로 매핑됨:

**색상**
- `--color-link-blue` — 주요 액션 색상 (버튼, 링크)
- `--color-learning` / `--color-learning-bg` — 학습/안내 강조 (주황)
- `--color-entry` / `--color-entry-bg` — 오류/위험 (빨강)
- `--color-task` / `--color-task-bg` — 성공/완료 (초록)
- `--color-purple` — 퍼플 강조 (`#8A58FF`)
- `--color-blue-10` — Link_Blue 틴트 배경
- `--color-neutral-0` ~ `--color-neutral-900` — 그레이 스케일
- `--color-gray-bg` — 페이지 배경 (`#f5f6f8`)

**레이아웃 상수**
- `--icon-sidebar-width: 52px` — 아이콘 전용 좌측 사이드바 폭
- `--sidebar-width: 220px` — 펼친 사이드바 폭
- `--header-height: 60px`
- `--sidebar-icon-bg: #16181D` — 아이콘 사이드바 다크 배경

**카드 토큰**
- `--card-bg / --card-border / --card-radius / --card-shadow`

---

### UI 컴포넌트 ([src/components/ui/](src/components/ui/))

#### `Button`
`variant(primary|secondary|outline|ghost|danger)` + `size(xs~xl)` + `loading` + `disabled`

#### `Badge`
`color(default|success|error|warning|info|blue)` + `size(sm|md)` + `dot`

color별 스타일은 `BADGE_CONFIG` Record로 통합 관리:
```tsx
const BADGE_CONFIG: Record<string, { wrapper: string; dot: string }> = {
  success: {
    wrapper: 'bg-[var(--color-task-bg)] text-[var(--color-task)]',
    dot:     'bg-[var(--color-task)]',
  },
  // ...
};
```

#### `Card`
카드형 컨테이너 공통 래퍼. `--card-*` 토큰 자동 적용.
```tsx
<Card className="overflow-hidden">...</Card>
```

#### `CircularProgress`
원형 진행 게이지 (SVG 기반).
- `percentage` (0~100), `size`, `strokeWidth`, `color`(hex 또는 CSS var), `labelColor`
- `labelColor`: 중앙 % 텍스트 색상. 미지정 시 `--color-text-primary`. StatCard에서 ring 색상과 동기화하여 전달.

```tsx
<CircularProgress percentage={75} size={68} strokeWidth={6} color="#8A58FF" labelColor="#8A58FF" />
```

> **주의**: Recharts `fill`/`stroke` 등 SVG presentation attribute에는 CSS 변수가 적용되지 않음. hex 값을 직접 사용할 것.

---

### 레이아웃 컴포넌트 ([src/components/layout/](src/components/layout/))

- `AppLayout` — 인증 후 기본 레이아웃 (IconSidebar + Header + 스크롤 콘텐츠 영역)
- `IconSidebar` — 좌측 52px 아이콘 네비게이션 바 (다크 배경)
- `Header` — 비인증 상태 헤더 (로그인/회원가입)
- `Sidebar` — 대시보드 좌측 네비게이션 (펼침/접힘 토글, react-router 연동)
  - 접힌 상태: 52px 아이콘 전용
  - 펼친 상태: 220px, 프로젝트 선택 + 네비게이션 + 사용자 정보

---

### 도메인 컴포넌트 ([src/components/common/](src/components/common/))

- `ProjectCard` — 프로젝트 카드 (썸네일, 공개/비공개 Badge, 태그, 수정일)
- `StatCard` — 대시보드 지표 카드 (원형 게이지, 퍼센트, warning 뱃지)
  - `color`: `blue|purple|green|orange` → ring 색상 + `labelColor` 연동
  - warning 뱃지: `bg-[var(--color-entry)] text-white` (solid red)
- `UsageTrendChart` — 사용량 추이 라인 차트 (주/월/년 세그먼트 탭)
- `SuccessRateChart` — 등록/삭제 비율 반원 도넛 게이지 (좌우 슬라이드 네비게이션)
- `DataTable` — 일일 데이터 통계 테이블
- `LogTable` — 로그 상세 테이블 (요청ID/FID: `break-all font-mono text-[13px]`)

#### DataTable 디자인 스펙 (Figma node 300-6664)

| 영역 | 값 |
|---|---|
| 헤더 배경 | `#f5f6f8` (`--color-gray-bg`) |
| 헤더 텍스트 | `#334155`, 14px **SemiBold** |
| 데이터 텍스트 | `#475569` (neutral-600), 14px Medium |
| 숫자 컬럼 정렬 | **center** (right 아님) |
| 날짜 컬럼 정렬 | left |
| 세로 구분선 | `등록`, `1:N 매칭` 컬럼에 `border-l border-r #e2e8f0` |
| 행 구분선 | `border-b #e8eef2` |
| 셀 패딩 | `px-4 py-3` |

---

### 스타일링 규칙

**CSS 변수 참조 방식** (Tailwind arbitrary value):
```tsx
className="bg-[var(--color-link-blue)] text-[var(--color-text-inverse)]"
```

**className 관리 패턴**:
```tsx
// 1. 다중 클래스 → 배열 join (멀티라인 문자열 금지 — 줄바꿈이 공백으로 처리됨)
const SHELL = [
  'fixed left-0 top-0',
  'bg-[var(--sidebar-bg)] border-r',
].join(' ');

// 2. 상태별 variant → Record
const BTN_STATE: Record<string, string> = {
  active:   'bg-[var(--nav-active-bg)] text-[var(--nav-active-text)]',
  inactive: 'text-[var(--nav-text)] hover:bg-[var(--nav-hover-bg)]',
};

// 3. 다중 속성 variant → 통합 Record
const CONFIG: Record<string, { wrapper: string; dot: string }> = {
  success: { wrapper: '...', dot: '...' },
};

// 4. 반복 JSX → 로컬 서브컴포넌트
function TableHeaderCell({ label, align }: Props) {
  return <th className={[TH_BASE, align === 'right' ? 'text-right' : ''].join(' ')}>{label}</th>;
}
```

**새 컴포넌트 추가 시**: `tokens.css`의 변수를 참조하고 각 `index.ts`에 export 추가.

---

### Path Alias

`@/` → `src/` (vite.config.ts + tsconfig.app.json에 설정됨)

```ts
import { Button, Card } from '@/components/ui';
import { Header } from '@/components/layout';
```
