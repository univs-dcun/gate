# 디자인 시스템 사용 가이드

> **Figma 기준**: `univs.gate_260206` — node `93-14647` (가이드) / node `300-6499` (대시보드 v2)
> **토큰 파일**: [`src/styles/tokens.css`](../src/styles/tokens.css)
> **스타일 파일**: [`src/index.css`](../src/index.css)

---

## 목차

1. [폰트 (Pretendard)](#1-폰트-pretendard)
2. [색상 토큰](#2-색상-토큰)
3. [타이포그래피](#3-타이포그래피)
4. [스페이싱 & 사이징](#4-스페이싱--사이징)
5. [Border Radius & 그림자](#5-border-radius--그림자)
6. [레이아웃 상수](#6-레이아웃-상수)
7. [Z-index & 트랜지션](#7-z-index--트랜지션)
8. [UI 컴포넌트](#8-ui-컴포넌트)
9. [레이아웃 컴포넌트](#9-레이아웃-컴포넌트)
10. [도메인 컴포넌트 (대시보드)](#10-도메인-컴포넌트-대시보드)
11. [스타일링 규칙](#11-스타일링-규칙)
12. [className 관리 패턴](#12-classname-관리-패턴)
13. [Tailwind CSS v4 사용법](#13-tailwind-css-v4-사용법)

---

## 1. 폰트 (Pretendard)

### 설정

폰트 파일은 `public/fonts/`에 위치하며 `tokens.css`의 `@font-face`로 로드됩니다.

```
public/
└── fonts/
    ├── Pretendard-Regular.otf    (weight: 400)
    ├── Pretendard-Medium.otf     (weight: 500)
    └── Pretendard-SemiBold.otf   (weight: 600)
```

> **주의**: Figma에서 Bold(700)로 지정된 스타일도 프로젝트에서는 SemiBold(600)로 적용합니다.
> 세 가지 웨이트만 로드하기 때문에 `font-bold` 대신 `font-semibold`를 사용하세요.

### CSS 변수

```css
--font-family: 'Pretendard', -apple-system, BlinkMacSystemFont, system-ui, ...;
--font-family-mono: 'Fira Code', Consolas, monospace;
```

### 폰트 웨이트 변수

| 변수 | 값 | 클래스 |
|---|---|---|
| `--font-regular` | 400 | `font-normal` |
| `--font-medium` | 500 | `font-medium` |
| `--font-semibold` | 600 | `font-semibold` |

---

## 2. 색상 토큰

### 브랜드 / 액션 색상

| 토큰 | 값 | 용도 |
|---|---|---|
| `--color-link-blue` | `#006FFF` | 주요 버튼, 링크, 활성 상태 |
| `--color-link-blue-hover` | `#005CD6` | hover 상태 |
| `--color-link-blue-active` | `#004DB3` | active 상태 |
| `--color-blue-10` | `#D6F0FF` | 버튼 배경 틴트, Badge info |
| `--color-blue-40` | `#BFDBFE` | 아바타, 강조 배경 |

### 시맨틱 색상

| 토큰 | 값 | 용도 |
|---|---|---|
| `--color-entry` | `#EF4444` | 오류 / 위험 / 삭제 |
| `--color-entry-bg` | `#FEF2F2` | 오류 배경 틴트 |
| `--color-task` | `#22C55E` | 성공 / 완료 |
| `--color-task-bg` | `#F0FDF4` | 성공 배경 틴트 |
| `--color-learning` | `#F59E0B` | 주의 / 진행 중 |
| `--color-learning-bg` | `#FFFBEB` | 주의 배경 틴트 |
| `--color-purple` | `#8A58FF` | 무료 체험 / 특별 강조 |
| `--color-purple-10` | `#F5F2FF` | Trial 배너 배경 |

### Neutral 스케일

| 토큰 | 값 | 주요 사용처 |
|---|---|---|
| `--color-neutral-0` | `#FFFFFF` | 카드 배경, 입력 배경 |
| `--color-neutral-50` | `#F8FAFC` | disabled 입력 배경 |
| `--color-neutral-100` | `#F1F5F9` | 테이블 헤더, hover 배경 |
| `--color-neutral-200` | `#E2E8F0` | 구분선, 트랙 색상 |
| `--color-neutral-300` | `#CBD5E1` | 카드 테두리, divider |
| `--color-neutral-400` | `#94A3B8` | placeholder, 보조 텍스트 |
| `--color-neutral-500` | `#64748B` | secondary 텍스트 |
| `--color-neutral-600` | `#475569` | 비활성 수치 텍스트 |
| `--color-neutral-700` | `#334155` | 본문 텍스트 |
| `--color-neutral-800` | `#1E293B` | 강조 텍스트 |
| `--color-neutral-900` | `#0F172A` | 최강조 텍스트 |
| `--color-gray-bg` | `#F5F6F8` | 페이지 / 패널 배경 |

### 시맨틱 Alias

```css
/* Surface */
--color-bg-page         /* 흰 배경 (카드, 헤더) */
--color-bg-surface      /* 회색 배경 (페이지 전체) */

/* Text */
--color-text-primary    /* #17191A — 기본 텍스트 */
--color-text-secondary  /* neutral-500 — 보조 텍스트 */
--color-text-tertiary   /* #757B80 — 3차 텍스트 */
--color-text-disabled   /* neutral-300 — 비활성 */
--color-text-inverse    /* #FFFFFF — 어두운 배경 위 텍스트 */

/* Border */
--color-border-default  /* #E8EEF2 — 기본 테두리 */
--color-border-strong   /* neutral-300 */
--color-border-focus    /* link-blue — 포커스 링 */
```

### 그래프 색상

차트 라인에 사용하는 4단계 블루 팔레트:

| 토큰 | 값 | 시리즈 |
|---|---|---|
| `--graph-color-1` | `#7FD3FC` | 등록 |
| `--graph-color-2` | `#4BBBFB` | 1:1 확인 |
| `--graph-color-3` | `#2799F9` | 1:N 매칭 |
| `--graph-color-4` | `#0B7CF8` | 라이브니스 |

---

## 3. 타이포그래피

### Figma 타입 스케일

모든 스타일 공통: `font-family: Pretendard`, `letter-spacing: -0.025em`, `line-height: 1.4`

| 스타일 | 크기 | 웨이트 | 토큰 | 유틸 클래스 |
|---|---|---|---|---|
| H1 | 42px | SemiBold(600) | `--type-h1-size/weight` | `.type-h1` |
| H2 | 36px | SemiBold(600) | `--type-h2-size/weight` | `.type-h2` |
| H3 | 32px | SemiBold(600) | `--type-h3-size/weight` | `.type-h3` |
| Body 1 | 16px | SemiBold(600) | `--type-body1-size/weight` | `.type-body1` |
| Body 2 | 14px | SemiBold(600) | `--type-body2-size/weight` | `.type-body2` |
| Label 1 | 16px | Medium(500) | `--type-label1-size/weight` | `.type-label1` |
| Label 2 | 14px | Medium(500) | `--type-label2-size/weight` | `.type-label2` |
| Label 3 | 13px | Regular(400) | `--type-label3-size/weight` | `.type-label3` |

**사용 예시:**

```tsx
// 유틸 클래스 사용 (index.css에 @layer utilities로 정의됨)
<h1 className="type-h1">제목</h1>
<p className="type-label1">본문 텍스트</p>

// 토큰 직접 참조 (커스텀 조합 시)
<p className="text-[var(--text-base)] font-medium tracking-[var(--tracking-tight)] leading-[var(--leading-normal)]">
  Label 1 스타일
</p>
```

### 폰트 크기 토큰

| 토큰 | 값 | 대응 Figma 스타일 |
|---|---|---|
| `--text-xxs` / `--text-xs` | 12px | Caption S |
| `--text-label3` | 13px | Label 3 |
| `--text-sm` | 14px | Label 2, Body 2 |
| `--text-base` | 16px | Label 1, Body 1 |
| `--text-md` / `--text-lg` | 18px | — |
| `--text-xl` | 24px | — |
| `--text-2xl` | 32px | H3 |
| `--text-3xl` | 36px | H2 |
| `--text-4xl` | 42px | H1 |

> **주의**: `h1`/`h2`/`h3` HTML 태그에는 전역 스타일이 적용됩니다.
> 카드 제목처럼 Label 크기(16px)를 써야 할 때는 `<p>` 태그에 토큰을 직접 적용하세요.

---

## 4. 스페이싱 & 사이징

### 스페이싱 토큰

| 토큰 | 값 |
|---|---|
| `--space-1` | 4px |
| `--space-2` | 8px |
| `--space-3` | 12px |
| `--space-4` | 16px |
| `--space-5` | 20px |
| `--space-6` | 24px |
| `--space-8` | 32px |
| `--space-10` | 40px |
| `--space-12` | 48px |

> 실제 스타일링에는 Tailwind 스페이싱 유틸리티(`p-4`, `gap-3` 등)를 주로 사용합니다.

### 컴포넌트 사이징

| 토큰 | 값 | 용도 |
|---|---|---|
| `--sizing-xs` | 24px | 버튼 xs 높이 |
| `--sizing-s` | 32px | 버튼 sm 높이 |
| `--sizing-m` | 40px | 버튼 lg 높이 |
| `--sizing-l` | 48px | 버튼 xl 높이 |

---

## 5. Border Radius & 그림자

### Border Radius

| 토큰 | 값 | 사용처 |
|---|---|---|
| `--radius-sm` | 4px | 버튼 xs, Checkbox, Pagination |
| `--radius-md` | 6px | 버튼 sm/md, Badge md |
| `--radius-xs` / `--radius-lg` | 8px | 버튼 lg/xl, 입력 필드, Segmented Control |
| `--radius-xl` | 12px | 카드 (`--card-radius`) |
| `--radius-2xl` | 16px | 대형 패널 |
| `--radius-full` | 999px | 알약형 버튼, 배지, 아바타 |

### 그림자

| 토큰 | 용도 |
|---|---|
| `--shadow-1` | Figma Shadow-1 — Segmented Control 활성 탭 |
| `--shadow-card` | 카드 (`--card-shadow`) — 매우 옅음 |
| `--shadow-xs` ~ `--shadow-xl` | 일반 엘리베이션 단계 |

**카드 토큰 묶음:**

```css
--card-bg:     var(--color-neutral-0)   /* 흰 배경 */
--card-border: var(--color-neutral-300) /* CBD5E1 */
--card-radius: var(--radius-xl)         /* 12px */
--card-shadow: var(--shadow-card)
```

```tsx
// 카드 컴포넌트에서 항상 이 4가지를 함께 사용
<div className="bg-[var(--card-bg)] border border-[var(--card-border)] rounded-[var(--card-radius)] shadow-[var(--card-shadow)]">
```

---

## 6. 레이아웃 상수

| 토큰 | 값 | 설명 |
|---|---|---|
| `--header-height` | 56px | 상단 헤더 높이 |
| `--icon-sidebar-width` | 52px | 아이콘 전용 좌측 사이드바 |
| `--sidebar-width` | 240px | 텍스트 사이드바 (DashboardLayout) |
| `--content-max-w` | 1280px | 콘텐츠 최대 너비 |

### 아이콘 사이드바 (AppLayout용)

```css
--sidebar-icon-bg:           #16181D    /* 다크 배경 */
--sidebar-icon-color:        #64748B    /* 기본 아이콘 색 */
--sidebar-icon-active-bg:    rgba(0,111,255,0.15)
--sidebar-icon-active-color: var(--color-link-blue)
--sidebar-icon-hover-bg:     rgba(255,255,255,0.06)
```

### 텍스트 사이드바 (DashboardLayout용)

```css
--sidebar-bg:              var(--color-neutral-0)
--sidebar-nav-active-bg:   var(--color-link-blue)
--sidebar-nav-active-text: var(--color-neutral-0)
--sidebar-nav-text:        var(--color-neutral-500)
--sidebar-nav-hover-bg:    var(--color-neutral-50)
```

### Interactive (Segmented Control)

```css
--interactive-bg-default:     #FFFFFF   /* 활성 탭 배경 */
--interactive-bg-hover:       #F2F8FC   /* 전체 컨트롤 배경 */
--interactive-text-default:   #17191A
--interactive-border-default: #E8EEF2   /* 활성 탭 테두리 */
```

---

## 7. Z-index & 트랜지션

### Z-index 레이어

```css
--z-base:     0
--z-dropdown: 100
--z-sticky:   200   /* 헤더 */
--z-overlay:  300
--z-modal:    400
--z-toast:    500
--z-tooltip:  600
```

### 트랜지션 속도

```css
--transition-fast:   100ms ease   /* 버튼 hover */
--transition-normal: 200ms ease   /* 일반 전환 */
--transition-slow:   300ms ease   /* 패널 열기/닫기 */
```

```tsx
className="transition-colors duration-[var(--transition-fast)]"
```

---

## 8. UI 컴포넌트

위치: [`src/components/ui/`](../src/components/ui/)

### Button

**파일**: [src/components/ui/Button.tsx](../src/components/ui/Button.tsx)

```tsx
import { Button } from '@/components/ui';
import type { Variant, Size } from '@/types';
// Variant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger'  (@/types/index.ts)
// Size    = 'xs' | 'sm' | 'md' | 'lg' | 'xl'                         (@/types/index.ts)

// interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement>
//   variant?   Variant   기본: 'primary'
//   size?      Size      기본: 'md'
//   loading?   boolean   스피너 표시 + disabled 처리
//   fullWidth? boolean   w-full 적용

<Button variant="primary" size="md">로그인</Button>
<Button variant="outline" size="sm">업그레이드</Button>
<Button variant="danger" size="md">삭제</Button>
<Button variant="primary" size="lg" loading>처리 중</Button>
<Button variant="secondary" size="md" disabled>비활성</Button>
<Button variant="primary" size="lg" fullWidth>전체 너비</Button>
```

**실제 variantStyles / sizeStyles** (Button.tsx 내부 패턴 그대로):

```tsx
const variantStyles: Record<Variant, string> = {
  primary: [
    'bg-[var(--color-link-blue)] text-[var(--color-text-inverse)]',
    'hover:bg-[var(--color-link-blue-hover)] active:bg-[var(--color-link-blue-active)]',
    'border border-transparent',
  ].join(' '),
  secondary: [
    'bg-[var(--color-neutral-100)] text-[var(--color-text-primary)]',
    'hover:bg-[var(--color-neutral-200)] active:bg-[var(--color-neutral-300)]',
    'border border-transparent',
  ].join(' '),
  outline: [
    'bg-transparent text-[var(--color-link-blue)] border border-[var(--color-link-blue)]',
    'hover:bg-[var(--color-blue-10)] active:bg-[var(--color-blue-40)]',
  ].join(' '),
  ghost: [
    'bg-transparent text-[var(--color-text-primary)]',
    'hover:bg-[var(--color-neutral-100)] active:bg-[var(--color-neutral-200)]',
    'border border-transparent',
  ].join(' '),
  danger: [
    'bg-[var(--color-entry)] text-[var(--color-text-inverse)]',
    'hover:bg-[var(--color-entry-hover)] active:bg-[var(--color-entry-active)]',
    'border border-transparent',
  ].join(' '),
};

const sizeStyles: Record<Size, string> = {
  xs: 'h-6  px-2  text-xs   rounded-[var(--radius-sm)] gap-1',
  sm: 'h-8  px-3  text-sm   rounded-[var(--radius-md)] gap-1.5',
  md: 'h-9  px-4  text-sm   rounded-[var(--radius-md)] gap-2',
  lg: 'h-10 px-5  text-base rounded-[var(--radius-lg)] gap-2',
  xl: 'h-12 px-6  text-base rounded-[var(--radius-lg)] gap-2',
};
```

**공통 베이스** (모든 variant에 항상 적용):

```
inline-flex items-center justify-center font-medium
transition-colors duration-[var(--transition-fast)]
focus:outline-none
focus-visible:ring-2 focus-visible:ring-[var(--color-border-focus)] focus-visible:ring-offset-2
disabled:bg-[var(--color-neutral-200)] disabled:text-[var(--color-text-disabled)]
disabled:border-transparent disabled:cursor-not-allowed
```

> `filter(Boolean).join(' ')` — 빈 문자열 클래스가 섞일 때 사용 (예: `fullWidth ? 'w-full' : ''`)

---

### Badge

**파일**: [src/components/ui/Badge.tsx](../src/components/ui/Badge.tsx)

```tsx
import { Badge } from '@/components/ui';
import type { ColorScheme } from '@/types';
// ColorScheme = 'default' | 'success' | 'warning' | 'error' | 'info'  (@/types/index.ts)
// color?: ColorScheme | 'blue'   size?: 'sm' | 'md'   dot?: boolean

<Badge color="success">공개</Badge>
<Badge color="error" dot>오류</Badge>
<Badge color="warning" size="md">진행중</Badge>
<Badge color="info">정보</Badge>
<Badge color="default">태그</Badge>
```

**실제 BADGE_CONFIG + SIZE_STYLES** (Badge.tsx 내부):

```tsx
const BADGE_CONFIG: Record<string, { wrapper: string; dot: string }> = {
  default: {
    wrapper: 'bg-[var(--color-neutral-100)] text-[var(--color-neutral-600)]',
    dot:     'bg-[var(--color-neutral-400)]',
  },
  success: {
    wrapper: 'bg-[var(--color-task-bg)] text-[var(--color-task)]',
    dot:     'bg-[var(--color-task)]',
  },
  error: {
    wrapper: 'bg-[var(--color-entry-bg)] text-[var(--color-entry)]',
    dot:     'bg-[var(--color-entry)]',
  },
  warning: {
    wrapper: 'bg-[var(--color-learning-bg)] text-[var(--color-learning)]',
    dot:     'bg-[var(--color-learning)]',
  },
  info: {
    wrapper: 'bg-[var(--color-blue-10)] text-[var(--color-link-blue)]',
    dot:     'bg-[var(--color-link-blue)]',
  },
  blue: {  // info와 동일
    wrapper: 'bg-[var(--color-blue-10)] text-[var(--color-link-blue)]',
    dot:     'bg-[var(--color-link-blue)]',
  },
};

const SIZE_STYLES: Record<string, string> = {
  sm: 'px-2 py-0.5 text-xs rounded-[var(--radius-sm)]',
  md: 'px-2.5 py-1 text-xs rounded-[var(--radius-md)]',
};

// 사용 패턴
const config = BADGE_CONFIG[color] ?? BADGE_CONFIG.default;
<span className={['inline-flex items-center gap-1 font-medium whitespace-nowrap', SIZE_STYLES[size], config.wrapper].join(' ')}>
  {dot && <span className={['w-1.5 h-1.5 rounded-full flex-shrink-0', config.dot].join(' ')} />}
  {children}
</span>
```

---

### Card

카드형 컨테이너 공통 래퍼. `--card-bg / --card-border / --card-radius / --card-shadow` 4종 토큰을 자동 적용합니다.

```tsx
import { Card } from '@/components/ui';

<Card>기본 카드</Card>
<Card className="overflow-hidden">추가 클래스 적용</Card>
<Card className="flex flex-col h-[381px]">높이 고정 카드</Card>
```

> DataTable, UsageTrendChart, SuccessRateChart의 최상위 컨테이너에서 사용 중.
> 카드 형태의 새 컴포넌트는 반드시 이 컴포넌트를 사용하세요.

---

### Input

**파일**: [src/components/ui/Input.tsx](../src/components/ui/Input.tsx)

```tsx
import { Input } from '@/components/ui';

// interface InputProps
//   value:      string          (필수)
//   onChange:   (v: string) => void  (필수, 문자열을 직접 받음)
//   type?:      'text' | 'email' | 'password'   기본: 'text'
//   label?:     string          입력 위 라벨
//   leftIcon?:  ReactElement    좌측 아이콘 (자동으로 pl-10 적용)
//   error?:     boolean         빨간 테두리 + 빨간 포커스 링   기본: false
//   id, placeholder, disabled … 네이티브 input 속성 사용 가능

<Input value={value} onChange={setValue} placeholder="이메일 입력" />
<Input value={value} onChange={setValue} label="이메일" error={hasError} id="email" />
<Input type="password" value={pw} onChange={setPw} />
<Input leftIcon={<LoginIdIcon size={18} />} value={value} onChange={setValue} />
```

**실제 border/shadow 클래스** (상태별):

```tsx
// 정상 상태
'border-[var(--color-border-default)]'
'focus:border-[var(--color-link-blue)]'
'focus:shadow-[0_0_0_3px_rgba(37,99,235,0.12)]'

// 오류 상태 (error=true)
'border-[var(--color-entry)]'
'focus:border-[var(--color-entry)]'
'focus:shadow-[0_0_0_3px_rgba(239,68,68,0.15)]'

// disabled
'bg-[var(--color-neutral-50)] cursor-not-allowed'
```

**입력 필드 공통 클래스:**

```
w-full h-11 text-sm border rounded-[var(--radius-lg)] outline-none
text-[var(--color-text-primary)] placeholder:text-[var(--color-text-disabled)]
transition-all duration-[var(--transition-fast)]
```

> `onChange`는 `(v: string) => void` — 네이티브 `onChange` (ChangeEvent)가 아닙니다.
> 우측 아이콘(clear/눈)은 값 유무와 type에 따라 자동으로 표시되며, padding이 자동 조정됩니다.

**`focus-within` 패턴** — 커스텀 Input 래퍼에서 부모 컨테이너에 포커스 스타일 적용 시:

```tsx
// CreateProjectModal의 입력 프레임 패턴
<div className={[
  'flex items-center gap-1 w-full h-[56px] px-2',
  'bg-white border rounded-[var(--radius-xs)] transition-colors',
  'border-[var(--color-border-default)] focus-within:border-[var(--color-border-focus)]',
].join(' ')}>
  <ProjectNameIcon size={24} />
  <input className="flex-1 bg-transparent outline-none ..." />
</div>
```

---

### Checkbox

**파일**: [src/components/ui/Checkbox.tsx](../src/components/ui/Checkbox.tsx)

```tsx
import { Checkbox } from '@/components/ui';

// interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'>
//   label?:         string   체크박스 우측 텍스트
//   indeterminate?: boolean  부분 선택 상태 (테이블 전체 선택 헤더)

<Checkbox label="전체 동의" checked={all} onChange={e => setAll(e.target.checked)} />
<Checkbox checked={checked} onChange={e => setChecked(e.target.checked)} />
<Checkbox label="비활성" disabled />
```

**`peer` 수정자 패턴** — 숨겨진 native input 기반 커스텀 스타일링:

```tsx
// native input을 sr-only로 숨기고, peer 수정자로 형제 요소에 상태 전달
<input type="checkbox" className="sr-only peer" />
<span className={[
  'w-4 h-4 rounded-[var(--radius-sm)] border border-[var(--color-border-strong)]',
  'bg-white transition-colors duration-[var(--transition-fast)]',
  'peer-checked:bg-[var(--color-link-blue)] peer-checked:border-[var(--color-link-blue)]',
  'peer-focus-visible:ring-2 peer-focus-visible:ring-[var(--color-border-focus)] peer-focus-visible:ring-offset-1',
].join(' ')} />
// 체크 아이콘: peer-checked:opacity-100 / 기본 opacity-0
```

---

### CircularProgress

StatCard 내부에서 사용하는 SVG 기반 원형 게이지.

```tsx
import { CircularProgress } from '@/components/ui';

<CircularProgress
  percentage={75}
  size={68}
  strokeWidth={6}
  color="var(--color-link-blue)"
  labelColor="var(--color-link-blue)"
/>

// showLabel=false로 텍스트 숨기기
<CircularProgress percentage={50} size={40} strokeWidth={4} color="var(--color-task)" showLabel={false} />
```

| props | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `percentage` | `number` (0~100) | — | |
| `size` | `number` (px) | 80 | |
| `strokeWidth` | `number` | 7 | |
| `color` | `string` | — | 링 색상 (CSS var 또는 hex) |
| `trackColor` | `string` | `--color-neutral-200` | 트랙 색상 |
| `showLabel` | `boolean` | `true` | 중앙 % 텍스트 표시 여부 |
| `labelColor` | `string` | `--color-text-primary` | 중앙 % 텍스트 색상 |

- `labelColor`는 StatCard에서 링 색상과 동기화하여 전달합니다.
- 중앙 텍스트 크기: `size < 60` → 10px, `size ≥ 60` → 16px

---

### PillTabs

**파일**: [src/components/ui/PillTabs.tsx](../src/components/ui/PillTabs.tsx)

탭 형태의 필터 UI (로그, 목록 필터링 등).

```tsx
import { PillTabs } from '@/components/ui';
import type { PillTab } from '@/components/ui';

// interface PillTab { id: string; label: string; count?: number; }
// interface PillTabsProps { tabs: PillTab[]; activeId: string; onChange: (id: string) => void; }

const tabs: PillTab[] = [
  { id: 'all',  label: '전체', count: 120 },  // count 있으면 "전체 (120)"으로 표시
  { id: 'reg',  label: '등록' },
  { id: 'v11',  label: '1:1 확인' },
];
const [active, setActive] = useState('all');

<PillTabs tabs={tabs} activeId={active} onChange={setActive} />
```

**실제 탭 버튼 클래스**:

```tsx
// 공통
'px-4 py-1.5 text-sm font-medium rounded-full transition-colors duration-[var(--transition-fast)]'

// 활성 탭
'bg-[var(--color-link-blue)] text-white'

// 비활성 탭
'text-[var(--color-text-secondary)] hover:bg-[var(--color-neutral-100)] hover:text-[var(--color-text-primary)]'
```

---

### Pagination

테이블 하단 페이지네이션.

```tsx
import { Pagination } from '@/components/ui';

<Pagination
  total={253}
  page={currentPage}
  pageSize={10}
  onChange={setCurrentPage}
/>
// "1 - 10 of 253 Pages" + 드롭다운 + 이전/다음 버튼
```

---

## 9. 레이아웃 컴포넌트

위치: [`src/components/layout/`](../src/components/layout/)

### AppLayout — 아이콘 사이드바 레이아웃

아이콘 전용 52px 사이드바(다크 테마) + 상단 헤더 구조.
비인증/일반 페이지(내 프로젝트, 프로젝트 생성 등)에 사용.

```tsx
import { AppLayout } from '@/components/layout';

function MyPage() {
  return (
    <AppLayout pageTitle="내 프로젝트">
      <div className="p-6">콘텐츠</div>
    </AppLayout>
  );
}
```

**구조:** `[IconSidebar 52px] + [Header 56px + 스크롤 콘텐츠]`

헤더 우측에는 언어 토글(KO/EN), 알림 벨, 유저 아바타가 있습니다.

---

### Sidebar — 대시보드 네비게이션 사이드바

**파일**: [src/components/layout/Sidebar.tsx](../src/components/layout/Sidebar.tsx)

react-router `useLocation`으로 현재 경로를 감지해 활성 상태를 자동 표시.
펼침(`var(--sidebar-width)`) ↔ 접힘(52px 아이콘 전용) 토글 지원.

```tsx
// DashboardLayout 내부에서 자동 사용 — 직접 import 불필요
<Sidebar />
```

**실제 스타일 상수 패턴** (Sidebar.tsx 내부):

```tsx
// 모듈 스코프 상수로 variant 클래스를 미리 조합
const SIDEBAR_SHELL = [
  'fixed left-0 top-0 bottom-0',
  'bg-[var(--sidebar-bg)] border-r border-[var(--color-border-default)]',
  'z-[var(--z-sticky)]',
].join(' ');

const NAV_BTN_BASE = [
  'flex items-center gap-2.5 w-full px-3 py-2 rounded-[var(--radius-md)] text-left',
  'text-sm font-medium transition-colors duration-[var(--transition-fast)]',
].join(' ');
const NAV_BTN_ACTIVE   = 'bg-[var(--sidebar-nav-active-bg)] text-[var(--sidebar-nav-active-text)]';
const NAV_BTN_INACTIVE = 'text-[var(--sidebar-nav-text)] hover:bg-[var(--sidebar-nav-hover-bg)] hover:text-[var(--color-text-primary)]';

// 사용
<button className={[NAV_BTN_BASE, isActive ? NAV_BTN_ACTIVE : NAV_BTN_INACTIVE].join(' ')}>
```

**CSS 변수 사이즈는 inline style로** (arbitrary value로 표현 불가할 때):

```tsx
// width에 CSS 변수를 써야 할 때 — className이 아닌 style 사용
<aside style={{ width: 'var(--sidebar-width)' }} />

// 반면 height는 토큰 값이 고정이면 arbitrary value 사용 가능
<header className="h-[var(--header-height)]" />
```

**펼친 상태**: 로고 + 접기 버튼 / 프로젝트 선택 드롭다운 / 네비게이션 메뉴 / 사용자 아바타 + 이메일
**접힌 상태**: 52px 아이콘 전용, `title` prop으로 툴팁 제공

---

### DashboardLayout — 텍스트 사이드바 레이아웃

240px 텍스트 사이드바 + 상단 플랜 바 + Trial 배너 구조.
로그인 후 대시보드 페이지에 사용.

```tsx
import { DashboardLayout } from '@/components/layout';

function DashboardPage() {
  return (
    <DashboardLayout>
      <div className="p-6">콘텐츠</div>
    </DashboardLayout>
  );
}
```

**구조:** `[Sidebar 240px] + [플랜 바 + (Trial 배너) + 스크롤 콘텐츠]`

Trial 배너는 닫기(X) 버튼으로 숨길 수 있습니다.

---

## 10. 도메인 컴포넌트 (대시보드)

위치: [`src/components/common/`](../src/components/common/)

### StatCard — 지표 카드

**파일**: [src/components/common/StatCard.tsx](../src/components/common/StatCard.tsx)

```tsx
import { StatCard } from '@/components/common';

// type StatColor = 'entry' | 'task' | 'learning' | 'blue'
// interface Delta { value: string; direction: 'up' | 'down'; }

<StatCard
  title="등록"
  value="8,282"
  unit="건"           // 기본값: '건'
  remaining="10건 남음"
  percentage={90}
  color="entry"
  warning="사용량 10% 남았어요!"               // 선택 — 링 위 말풍선 배지
  delta={{ value: '24건', direction: 'down' }} // 선택 — 변화량 칩 (down: 화살표 반전)
/>
```

**실제 color → CircularProgress color/labelColor 매핑** (StatCard.tsx 내부):

```tsx
const ringColors: Record<StatColor, string> = {
  entry:    'var(--color-entry)',      // 빨강 — 위험/초과
  task:     'var(--color-task)',       // 초록 — 안전/여유
  learning: 'var(--color-learning)',   // 주황 — 주의
  blue:     'var(--color-link-blue)',  // 파랑 — 일반
};

// StatCard 내부 CircularProgress 호출
<CircularProgress
  percentage={percentage}
  size={56}
  strokeWidth={5}
  color={ringColors[color]}
  labelColor={ringColors[color]}  // 링 색상 = 중앙 % 텍스트 색상
/>
```

> StatCard는 `<Card>` 컴포넌트를 쓰지 않고 `--card-border / --card-radius / --card-shadow` 토큰을 직접 참조합니다.
> 배경은 `style={{ backgroundImage: 'linear-gradient(...)' }}`로 인라인 처리합니다.

**delta 칩**: `bg-[var(--color-neutral-100)] rounded-[var(--radius-xs)] pl-2 pr-1 py-1`
  - 방향: `direction === 'down'` → `-scale-y-100` 클래스로 화살표 SVG를 Y축 반전

**warning 말풍선**: 링 위 `absolute` 오버레이, CSS 인라인 삼각형 꼬리
  - 배경: `bg-[var(--color-entry)] text-white rounded-full` / `text-[length:var(--text-xxs)]`
  - 꼬리: `style={{ borderTop: '5px solid var(--color-entry)', borderLeft/Right: '4px solid transparent' }}`

---

### UsageTrendChart — 사용량 추이 라인 차트

Segmented Control(주/월/년)로 기간을 전환하는 recharts LineChart.

```tsx
import { UsageTrendChart } from '@/components/common';

<UsageTrendChart />   // 자체 상태 관리, props 없음
```

- 높이 고정: `h-[381px]`
- 라인 4개: 등록 / 1:1 확인 / 1:N 매칭 / 라이브니스

| 시리즈 | Hex | 비고 |
|---|---|---|
| 등록 | `#8A58FF` | `--color-purple` |
| 1:1 확인 | `#4BBBFB` | `--graph-color-2` |
| 1:N 매칭 | `#2799F9` | `--graph-color-3` |
| 라이브니스 | `#0B7CF8` | `--graph-color-4` |

> **주의**: recharts `stroke`/`fill` 같은 SVG presentation attribute에는 CSS 변수가 동작하지 않습니다.
> 차트 색상은 반드시 **hex 값**을 직접 사용하세요.

---

### SuccessRateChart — 등록/삭제 비율 반원 게이지

recharts 반원형 도넛 차트. `cy="100%"` + 절댓값 반지름으로 너비를 꽉 채웁니다.
좌/우 네비게이션 화살표 + 페이지네이션 도트 포함.

```tsx
import { SuccessRateChart } from '@/components/common';

<SuccessRateChart registerRate={63} />
<SuccessRateChart registerRate={80} title="커스텀 제목" />
```

| props | 타입 | 기본값 |
|---|---|---|
| `registerRate` | `number` (0~100) | 63 |
| `title` | `string` | `'등록/삭제 비율'` |

- 높이 고정: `h-[381px]`
- 게이지 컨테이너: 210px, `outerRadius={150}`, `innerRadius={105}`
- 등록: `#8A58FF` / 삭제: `#E2E8F0` (SVG fill → hex 직접 사용)
- 좌우 화살표로 슬라이드 전환 (총 4슬라이드 구조)

> **recharts 반원 팁**: `%` 반지름은 `min(width, height)/2` 기준으로 계산되어
> 너비를 채울 수 없습니다. 반원이 작아 보일 때는 절댓값(px)을 사용하세요.

---

### DataTable — 일일 데이터 통계 테이블

```tsx
import { DataTable } from '@/components/common';

<DataTable />   // 현재 mock 데이터 사용
```

컬럼: 날짜 UTC+0 / 등록 / 1:1 확인 / 1:N 매칭 / 라이브니스

#### Figma 디자인 스펙 (node 300-6664)

| 영역 | 값 | 비고 |
|---|---|---|
| 헤더 배경 | `#f5f6f8` | `--color-gray-bg` |
| 헤더 텍스트 | `#334155` 14px **SemiBold** | `--color-neutral-700` |
| 데이터 텍스트 | `#475569` 14px Medium | `--color-neutral-600` |
| 숫자 컬럼 정렬 | `text-center` | right 아님 |
| 날짜 컬럼 정렬 | `text-center` | |
| 세로 구분선 | `border-l border-r #e2e8f0` | `등록`, `1:N 매칭` 컬럼에 적용 |
| 행 구분선 | `border-b #e8eef2` | `--color-border-default` |
| 셀 패딩 | `px-4 py-3` | |

---

### LogTable — 로그 목록 테이블

**파일**: [src/components/common/LogTable.tsx](../src/components/common/LogTable.tsx)

```tsx
import { LogTable } from '@/components/common';
import type { LogEntry } from '@/components/common/LogTable';

// type LogModule = '등록' | '1:1 확인' | '1:N 매칭' | '라이브니스'
// type LogResult = '성공' | '실패' | '리얼' | '페이크'
// interface LogEntry { id, module, requestId, result, fid?, score?, memo?, createdAt }

<LogTable data={logEntries} totalCount={253} />
```

**Config Record with inline style** — 동적 색상을 CSS 변수가 아닌 hex로 관리해야 할 때:

```tsx
// 모듈별 색상은 style={{ color }} 로 적용 (tailwind로 동적 클래스 불가)
const MODULE_CONFIG: Record<LogModule, { color: string; iconType: string }> = {
  '등록':     { color: '#2799f9', iconType: 'enroll' },
  '1:1 확인': { color: '#f59e0b', iconType: 'verify' },
  '1:N 매칭': { color: '#8b5cf6', iconType: 'match' },
  '라이브니스': { color: '#10b981', iconType: 'liveness' },
};

// 결과 배지 — bg + text 조합을 인라인 style로
const RESULT_CONFIG: Record<LogResult, { bg: string; text: string }> = {
  '성공':  { bg: '#eff9ff', text: '#006fff' },
  '리얼':  { bg: '#eff9ff', text: '#006fff' },
  '실패':  { bg: '#fff7f6', text: '#d83232' },
  '페이크': { bg: '#fff7f6', text: '#d83232' },
};

// 사용
const mod = MODULE_CONFIG[row.module];
<span style={{ color: mod.color }}>{row.module}</span>

const res = RESULT_CONFIG[row.result];
<span style={{ backgroundColor: res.bg, color: res.text }}>{row.result}</span>
```

> Tailwind arbitrary value `text-[#2799f9]`는 정적 값엔 쓸 수 있지만,
> **런타임 변수 색상**(`mod.color`)은 반드시 `style={}` 로 처리하세요.

---

## 11. 스타일링 규칙

### CSS 변수 참조 방식

Tailwind CSS v4의 arbitrary value 문법으로 CSS 변수를 참조합니다.

```tsx
// 올바른 사용법
className="bg-[var(--color-link-blue)] text-[var(--color-text-inverse)]"
className="rounded-[var(--radius-xl)] shadow-[var(--card-shadow)]"
className="text-[var(--text-base)] font-medium tracking-[var(--tracking-tight)]"

// inline style이 필요한 경우 (변수 단독)
style={{ backgroundColor: 'var(--sidebar-icon-bg)' }}
```

### 새 컴포넌트 추가 체크리스트

1. `tokens.css`의 변수를 참조하여 하드코딩된 색상/크기 사용 금지
2. 카드 형태라면 `--card-bg / --card-border / --card-radius / --card-shadow` 4종 세트 사용
3. 텍스트는 `--color-text-primary / secondary / tertiary` 시맨틱 토큰 우선 사용
4. 각 폴더의 `index.ts`에 export 추가

### 경로 alias

```ts
import { Button } from '@/components/ui';
import { DashboardLayout } from '@/components/layout';
import { StatCard } from '@/components/common';
```

`@/` → `src/` (vite.config.ts + tsconfig.app.json에 설정됨)

### `h1`/`h2`/`h3` 태그 주의사항

`index.css`에 전역 헤딩 스타일이 정의되어 있습니다.

```css
h1 { font-size: var(--type-h1-size);  /* 42px */ }
h2 { font-size: var(--type-h2-size);  /* 36px */ }
h3 { font-size: var(--type-h3-size);  /* 32px */ }
```

카드 제목처럼 Label 크기(16px)가 필요할 때는 `<h3>` 대신 `<p>` 태그를 사용하세요.

```tsx
// 잘못된 예: 전역 h3 스타일(32px)이 충돌
<h3 className="text-[var(--text-base)]">카드 제목</h3>

// 올바른 예
<p className="text-[var(--text-base)] font-semibold text-[var(--color-neutral-700)] tracking-[var(--tracking-tight)]">
  카드 제목
</p>
```

---

## 12. className 관리 패턴

컴포넌트의 가독성과 일관성을 위해 아래 패턴을 따릅니다.

### 1. 다중 클래스 → 배열 `join(' ')`

멀티라인 문자열 안의 줄바꿈은 의도치 않은 공백으로 처리됩니다. **배열 join 패턴**을 사용하세요.

```tsx
// 금지: 줄바꿈이 실제 공백으로 포함됨
className="fixed left-0 top-0
           bg-[var(--sidebar-bg)]"

// 권장
const SHELL = [
  'fixed left-0 top-0',
  'bg-[var(--sidebar-bg)] border-r',
].join(' ');
```

### 2. 상태별 variant → `Record<string, string>`

```tsx
const BTN_STATE: Record<string, string> = {
  active:   'bg-[var(--nav-active-bg)] text-[var(--nav-active-text)]',
  inactive: 'text-[var(--nav-text)] hover:bg-[var(--nav-hover-bg)]',
};

// 사용
className={[BTN_BASE, isActive ? BTN_STATE.active : BTN_STATE.inactive].join(' ')}
```

### 3. 다중 속성 variant → 통합 `Record`

연관된 여러 속성(배경+텍스트, 래퍼+점)을 하나의 Record로 묶습니다.

```tsx
// Badge의 BADGE_CONFIG 패턴
const BADGE_CONFIG: Record<string, { wrapper: string; dot: string }> = {
  success: {
    wrapper: 'bg-[var(--color-task-bg)] text-[var(--color-task)]',
    dot:     'bg-[var(--color-task)]',
  },
  error: {
    wrapper: 'bg-[var(--color-entry-bg)] text-[var(--color-entry)]',
    dot:     'bg-[var(--color-entry)]',
  },
};

const config = BADGE_CONFIG[color] ?? BADGE_CONFIG.default;
// config.wrapper, config.dot 으로 각각 접근
```

### 4. 반복 JSX → 로컬 서브컴포넌트

동일한 구조의 JSX가 3회 이상 반복될 때 파일 내 서브컴포넌트로 추출합니다.

```tsx
// 공통 상수 추출
const TH_BASE = [
  'px-4 py-3 text-sm font-semibold text-[#334155]',
  'bg-[#f5f6f8] tracking-[var(--tracking-tight)]',
].join(' ');

// 서브컴포넌트
function TableHeaderCell({ label, align }: { label: string; align: 'left' | 'center' }) {
  return (
    <th className={[TH_BASE, align === 'center' ? 'text-center' : 'text-left'].join(' ')}>
      {label}
    </th>
  );
}
```

### 5. Recharts SVG 색상 → hex 직접 사용

SVG presentation attribute(`fill`, `stroke`)는 CSS 변수를 지원하지 않습니다.

```tsx
// 금지
<Line stroke="var(--color-purple)" />

// 권장
const LINES = [
  { key: 'reg', hex: '#8A58FF' },  // --color-purple
];
<Line stroke={line.hex} />
```

### 6. 빈 문자열 클래스 → `filter(Boolean).join(' ')`

조건부 클래스가 `false`/`''`를 반환할 수 있을 때, 불필요한 공백을 제거합니다.

```tsx
// fullWidth가 false면 ''이 포함되어 공백이 남음 → filter(Boolean)으로 제거
<button
  className={[
    'inline-flex items-center justify-center font-medium',
    variantStyles[variant],
    sizeStyles[size],
    fullWidth ? 'w-full' : '',     // false 케이스: 빈 문자열
    className,                     // 외부 className이 undefined일 수도 있음
  ]
    .filter(Boolean)
    .join(' ')}
/>

// join(' ')만 쓸 때: 조건 클래스가 항상 문자열로 확정될 때 (삼항 연산자 두 값 모두 문자열)
className={[NAV_BTN_BASE, isActive ? NAV_BTN_ACTIVE : NAV_BTN_INACTIVE].join(' ')}
```

### 7. 모듈 스코프 스타일 상수 — 컴포넌트 외부에 선언

파일 전체에서 재사용되는 variant 클래스는 컴포넌트 바깥 모듈 스코프에 상수로 선언합니다.
렌더마다 재생성을 방지하고, 한 파일 안에서 참조하는 모든 위치가 통일됩니다.

```tsx
// 파일 최상단 (컴포넌트 함수 밖)
const SIDEBAR_SHELL = [
  'fixed left-0 top-0 bottom-0',
  'bg-[var(--sidebar-bg)] border-r border-[var(--color-border-default)]',
  'z-[var(--z-sticky)]',
].join(' ');

const ICON_BTN_BASE = 'flex h-8 w-8 items-center justify-center rounded-[var(--radius-md)] transition-colors';
const ICON_BTN_ACTIVE   = 'bg-[var(--sidebar-nav-active-bg)] text-[var(--sidebar-nav-active-text)]';
const ICON_BTN_INACTIVE = 'text-[var(--sidebar-nav-text)] hover:bg-[var(--sidebar-nav-hover-bg)]';

const FIELD_LABEL = [
  'text-[14px] font-semibold text-[var(--color-neutral-700)]',
  'tracking-[-0.35px] leading-[1.4]',
].join(' ');
```

### 8. 런타임 동적 색상 → `style={}`

Tailwind arbitrary value는 **정적 리터럴**만 지원합니다. 변수에서 읽어온 색상은 `style={}` 를 사용하세요.

```tsx
// 금지 — 런타임 변수는 Tailwind 클래스로 동작하지 않음
<span className={`text-[${dynamicColor}]`} />

// 권장 — style로 직접 주입
<span style={{ color: mod.color }}>...</span>
<span style={{ backgroundColor: res.bg, color: res.text }}>...</span>

// 예외: tokens.css 변수 문자열은 style에서 그대로 사용 가능
const ringColors: Record<StatColor, string> = {
  entry: 'var(--color-entry)',
  blue:  'var(--color-link-blue)',
};
<CircularProgress color={ringColors[color]} />  // SVG style prop에 전달
```

---

## 13. Tailwind CSS v4 사용법

### 셋업 개요

이 프로젝트는 **Tailwind CSS v4** + **`@tailwindcss/vite`** 플러그인을 사용합니다.
v3와 달리 `tailwind.config.js` 파일이 없고, CSS가 설정의 진입점입니다.

```ts
// vite.config.ts
import tailwindcss from '@tailwindcss/vite'
plugins: [react(), tailwindcss()]
```

```css
/* src/index.css */
@import "tailwindcss";   /* base + components + utilities 전체 로드 */
```

---

### CSS 변수 × Arbitrary Value

Tailwind의 `[]` 문법으로 `tokens.css` 변수를 직접 참조합니다.

```tsx
className="bg-[var(--color-link-blue)]"
className="text-[var(--color-text-primary)]"
className="rounded-[var(--radius-xl)]"
className="shadow-[var(--card-shadow)]"
className="h-[var(--header-height)]"
className="w-[var(--sidebar-width)]"
className="transition-colors duration-[var(--transition-fast)]"
```

> **규칙**: `bg-blue-500`, `text-gray-700` 같은 Tailwind 기본 팔레트 색상은 사용하지 않습니다.
> 색상은 반드시 `tokens.css` 변수를 arbitrary value로 참조하세요.

---

### 커스텀 유틸리티 클래스 (index.css)

`@layer utilities`에 정의된 프로젝트 전용 클래스로, Tailwind 유틸과 동일한 우선순위로 동작합니다.

#### 텍스트 색상

| 클래스 | 적용 변수 | 용도 |
|---|---|---|
| `text-primary` | `--color-text-primary` | 기본 본문 텍스트 |
| `text-secondary` | `--color-text-secondary` | 보조 텍스트 |
| `text-disabled` | `--color-text-disabled` | 비활성 텍스트 |
| `text-inverse` | `--color-text-inverse` | 어두운 배경 위 텍스트 |

#### 배경 색상

| 클래스 | 적용 변수 | 용도 |
|---|---|---|
| `bg-page` | `--color-bg-page` | 카드, 헤더 — 흰 배경 |
| `bg-surface` | `--color-bg-surface` | 페이지 전체 — 회색 배경 |
| `bg-elevated` | `--color-bg-elevated` | 팝업, 드롭다운 |

#### 타이포그래피 유틸

`font-size + font-weight + line-height + letter-spacing`을 한 번에 적용합니다.

| 클래스 | 크기 | 웨이트 |
|---|---|---|
| `type-h1` / `type-h2` / `type-h3` | 42 / 36 / 32px | SemiBold(600) |
| `type-body1` / `type-body2` | 16 / 14px | SemiBold(600) |
| `type-label1` / `type-label2` | 16 / 14px | Medium(500) |
| `type-label3` | 13px | Regular(400) |

```tsx
<h1 className="type-h1">페이지 제목</h1>
<p className="type-body1 text-secondary">설명 텍스트</p>
<span className="type-label2 text-primary">레이블</span>
```

---

### @layer 레이어 구조

```css
@import "tailwindcss";      /* 1. Tailwind base + utilities 전체 */

@layer base {
  /* 2. 전역 HTML 기본 스타일 — body, h1~h3, a */
  /*    tokens.css 변수를 참조하는 리셋 수준 스타일만 작성 */
}

@layer utilities {
  /* 3. 프로젝트 커스텀 유틸 — text-primary, bg-page, type-* 등 */
  /*    Tailwind 유틸과 동일한 우선순위 → 충돌 시 소스 순서가 우선 */
}
```

> `@layer components`는 현재 미사용. 재사용 UI는 React 컴포넌트(`src/components/ui/`)로 관리합니다.

---

### 자주 쓰는 유틸리티 패턴

실제 컴포넌트에서 추출한 패턴들입니다.

```tsx
/* ── 레이아웃 ── */
className="flex items-center gap-2"
className="flex items-center justify-between"
className="flex flex-col gap-4"
className="flex-1 overflow-y-auto"           // 스크롤 영역
className="flex-shrink-0"                   // 고정 크기 영역 (헤더, 하단)
className="grid grid-cols-4 gap-4"
className="relative flex items-center"      // 아이콘 absolute 포지셔닝 부모

/* ── 위치 / 크기 ── */
className="fixed inset-0"                   // 오버레이 풀스크린
className="absolute inset-0"               // 부모 기준 풀스크린
className="absolute right-2 flex items-center gap-0.5"  // 우측 아이콘 그룹
className="fixed left-0 top-0 bottom-0"    // 사이드바
className="w-full min-w-[900px]"           // 테이블 (가로 스크롤 포함)
className="max-w-[calc(100vw-36px)]"       // calc로 동적 제한

/* ── Z-index (CSS 변수로) ── */
className="z-[var(--z-sticky)]"    // 헤더, 사이드바
className="z-[var(--z-modal)]"     // 모달
className="z-[var(--z-toast)]"     // 토스트
className="z-10"                   // 컴포넌트 내부 오버레이 (말풍선 등)

/* ── 텍스트 ── */
className="truncate"                              // 한 줄 말줄임
className="whitespace-nowrap"                     // 줄바꿈 금지
className="type-label2 text-secondary"            // 유틸 클래스 조합
className="text-xs font-medium whitespace-nowrap" // Badge 기본 텍스트
className="tracking-[-0.35px]"                   // 자간 (Figma 스펙 직접)

/* ── 테두리 / 구분선 ── */
className="border border-[var(--color-border-default)]"
className="border-b border-[var(--color-border-default)] flex-shrink-0"
className="border-t border-[var(--color-border-default)]"
className="border-r border-[var(--color-border-default)]"

/* ── 트랜지션 ── */
className="transition-colors duration-[var(--transition-fast)]"   // 버튼, 탭
className="transition-all duration-[var(--transition-fast)]"      // Input (border + shadow)
className="transition-colors"                                      // 단순 색상 전환

/* ── 상호작용 ── */
className="cursor-pointer select-none"
className="cursor-not-allowed opacity-50"
className="hover:bg-[var(--color-neutral-100)]"
className="hover:bg-[var(--color-entry-bg)] hover:text-[var(--color-entry)]"  // 로그아웃 버튼
className="disabled:bg-[var(--color-neutral-200)] disabled:text-[var(--color-text-disabled)] disabled:cursor-not-allowed"

/* ── 포커스 ── */
className="focus:outline-none"
className="focus-visible:ring-2 focus-visible:ring-[var(--color-border-focus)] focus-visible:ring-offset-2"
className="focus:border-[var(--color-link-blue)] focus:shadow-[0_0_0_3px_rgba(37,99,235,0.12)]"   // Input 포커스
className="focus:border-[var(--color-entry)]    focus:shadow-[0_0_0_3px_rgba(239,68,68,0.15)]"    // Input 오류 포커스
className="focus-within:border-[var(--color-border-focus)]"  // 컨테이너 내부 포커스

/* ── peer 수정자 (Checkbox) ── */
className="sr-only peer"                                                   // 숨김 input
className="peer-checked:bg-[var(--color-link-blue)]"                      // 체크 상태 커스텀 박스
className="peer-checked:opacity-100"                                       // 체크 아이콘 표시
className="peer-focus-visible:ring-2 peer-focus-visible:ring-offset-1"   // 포커스 링

/* ── 모달 / 오버레이 ── */
className="fixed inset-0 z-[var(--z-modal)] flex"
className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]"  // 딤 배경
className="bg-white rounded-[16px] shadow-[var(--shadow-xl)]"             // 모달 패널
```

---

### 금지 / 권장 요약

| 상황 | 금지 | 권장 |
|---|---|---|
| 색상 | `bg-blue-500` | `bg-[var(--color-link-blue)]` |
| 텍스트 색 | `text-gray-500` | `text-secondary` 또는 `text-[var(--color-neutral-500)]` |
| 폰트 굵기 | `font-bold` (700) | `font-semibold` (600) |
| 타이포그래피 | `text-sm font-medium` 단독 | `type-label2` 유틸 클래스 |
| Recharts 색상 | `stroke="var(--color-purple)"` | `stroke="#8A58FF"` (hex 직접) |
| 런타임 동적 색 | `` className={`text-[${color}]`} `` | `style={{ color }}` |
| 줄바꿈 className | 멀티라인 문자열 | 배열 `join(' ')` |
| 빈 클래스 혼합 | `.join(' ')` | `.filter(Boolean).join(' ')` |
