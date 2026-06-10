# 핸드오프 가이드 — UNIVS Gate Web

다른 PC나 팀원이 이 프로젝트를 이어서 작업하기 위한 가이드입니다.

---

## 1. 환경 설정

### 필수 요구사항
- **Node.js** v18+ (권장 v20+)
- **npm** v9+
- Git

### 저장소 클론
```bash
git clone https://bitbucket.org/univsai/univsaigateserviceweb.git
cd univsaigateserviceweb
git checkout develop
npm install
```

### 환경변수 설정
```bash
cp .env.example .env.local
```
`.env.local` 파일을 열어 실제 값으로 수정:
```
VITE_API_BASE_URL=https://api-dev.univsgate.com/api
VITE_MOBILE_BASE_URL=https://demo-dev.univsgate.com
VITE_API_GUIDE_URL=https://...
```

> `public/config.js`에서 런타임 설정도 확인 (배포 환경용)

### 개발 서버 실행
```bash
npm run dev        # http://localhost:5173
npm run build      # 프로덕션 빌드
npm run preview    # 빌드 결과 미리보기
```

---

## 2. 기술 스택

| 항목 | 버전/도구 |
|---|---|
| 프레임워크 | React 19 + Vite |
| 언어 | TypeScript |
| 스타일 | Tailwind CSS v4 |
| 상태 관리 | TanStack Query v5 |
| 라우팅 | react-router-dom v7 |
| i18n | react-i18next |
| 차트 | Recharts |
| HTTP | Axios |

---

## 3. 디렉토리 구조

```
src/
├── components/
│   ├── ui/          # 원자 단위 (Button, Badge, Card, Toggle 등)
│   ├── layout/      # DashboardLayout, Sidebar, WelcomeLayout
│   └── common/      # 도메인 공통 (LogTable, DataTable, StatCard 등)
├── pages/           # 라우트별 페이지
├── services/        # API 호출 (http.ts, project.ts, log.ts 등)
├── contexts/        # ProjectContext
├── hooks/           # 커스텀 훅
├── i18n/
│   ├── index.ts
│   └── locales/
│       ├── ko.json  # 한국어
│       └── en.json  # 영어
├── styles/
│   └── tokens.css   # 디자인 토큰 (CSS 변수)
└── types/
```

---

## 4. 주요 파일 경로

| 역할 | 경로 |
|---|---|
| 라우팅 | `src/App.tsx` |
| HTTP 클라이언트 | `src/services/http.ts` |
| 디자인 토큰 | `src/styles/tokens.css` |
| 프로젝트 컨텍스트 | `src/contexts/ProjectContext.tsx` |
| 대시보드 레이아웃 | `src/components/layout/DashboardLayout.tsx` |
| 사이드바 | `src/components/layout/Sidebar.tsx` |
| 한국어 번역 | `src/i18n/locales/ko.json` |
| 영어 번역 | `src/i18n/locales/en.json` |

---

## 5. Git 워크플로우

### 브랜치 전략 (Git Flow)
```
main          ← 운영 배포
develop       ← 개발 통합 (현재 작업 브랜치)
feature/UG-*  ← 기능 개발
```

### 작업 순서
```bash
# 1. Jira 이슈 생성 후 브랜치 생성
git flow feature start UG-XXX-기능명

# 2. 작업 후 커밋
git add <files>
git commit -m "feat: 설명"

# 3. develop 머지
git flow feature finish UG-XXX-기능명

# 4. 원격 push
git push origin develop
```

### Jira 연동
```bash
jira issue move UG-XXX "진행 중"
jira issue comment add UG-XXX "작업 내용"
jira issue move UG-XXX "완료"
```

---

## 6. 개발 규칙

### i18n
- 화면에 노출되는 **모든 텍스트**는 `ko.json` + `en.json`에 키 추가
- 키 네임스페이스: `섹션.키` (예: `common.save`, `logs.result`)
- 하드코딩 한국어 절대 금지

### 스타일링
```tsx
// CSS 변수 사용
className="bg-[var(--color-link-blue)]"

// 다중 클래스 → 배열 join
const STYLE = ['class1', 'class2'].join(' ');

// 상태별 variant → Record
const CONFIG: Record<string, string> = { active: '...', inactive: '...' };
```

### 컴포넌트
- 새 컴포넌트 추가 시 `src/components/common/index.ts`에 export 추가
- `tokens.css`의 CSS 변수 우선 사용

---

## 7. API 연동

### 인증
- `localStorage('access_token')` Bearer 토큰 자동 첨부
- 401 응답 → `/login` 리다이렉트
- 네트워크 오류 → `/network-error` 리다이렉트

### 주요 엔드포인트
| 기능 | 메서드 | 경로 |
|---|---|---|
| 프로젝트 목록 | GET | `/v1/projects` |
| 프로젝트 설정 | GET | `/v1/projects/{id}/settings` |
| 대시보드 요약 | GET | `/v1/dashboard/summary` |
| 로그 목록 | GET | `/v1/match` |
| 특징점 목록 | GET | `/v1/users` |
| 동의 이력 | GET | `/v1/projects/{id}/settings/consent/logs` |
| SDK QR 발급 | GET | `/v1/sdk/*/qr` |

---

## 8. 현재 상태 및 주요 변경 이력 (2026-06-04 기준)

### 완료된 주요 작업
- ✅ 전체 페이지 max-width 1920px 중앙정렬
- ✅ 사이드바 Figma 디자인 반영 (default/hover/selected)
- ✅ 개인정보 동의 UI 개선 (설정 페이지, 헤더 배지, 확인 팝업)
- ✅ consentSnapshot 기반 이미지 비공개 처리
- ✅ 동의 변경 이력 팝업 API 연동
- ✅ 팝업 z-index 통일 (전체 화면 가림)
- ✅ i18n 하드코딩 전수 교체
- ✅ 특징점 관리 상세 팝업 (개인정보 동의 시)
- ✅ 테이블 UI 개선 (정렬, 구분선, 패딩, 이미지 크기)
- ✅ 로그인 후 대시보드 데이터 미로딩 버그 수정 (v0.0.3)
- ✅ 로그아웃 시 이전 세션 캐시 정리 (v0.0.3)

### 알려진 이슈 / TODO
- 개인정보 동의 이력 페이지네이션 미구현 (현재 전체 목록 반환)
- SDK 탭 숨김 처리 중 (`testMode: 'demo'` 고정)

---

## 9. 로컬 개발 시 주의사항

1. **API 서버**: `api-dev.univsgate.com` (VPN 또는 네트워크 접근 필요할 수 있음)
2. **`.env.local`**: git에 포함되지 않으므로 직접 생성 필요
3. **폰트**: `public/fonts/` 디렉토리의 Pretendard 폰트 파일 필요
4. **최소 화면 너비**: 1320px (사이드바 220px + 콘텐츠 1100px)

---

## 10. 문의

- Jira 프로젝트: `UG` (https://univsai.atlassian.net)
- API 문서: Swagger (`/swagger-ui.html`)
- 담당: sbk@univs.ai
