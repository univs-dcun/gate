# Frontend Coding Conventions

Airbnb React/JSX Style Guide 기반, Gate Service 프로젝트(React 18 + TypeScript) 특화 규칙 포함.

---

## 1. 파일 & 컴포넌트 네이밍

- 파일명: PascalCase + `.tsx` 확장자 (`UserCard.tsx`)
- 컴포넌트 참조: PascalCase, 인스턴스: camelCase
- 훅 파일: `use` 접두사 + camelCase (`useAuthToken.ts`)
- 유틸/헬퍼: camelCase (`formatDate.ts`)
- **파일 하나에 컴포넌트 하나** (단, 작은 stateless 컴포넌트 복수 허용)

---

## 2. 컴포넌트 작성

- **함수형 컴포넌트 + 훅** 사용 (클래스 컴포넌트 금지)
- 컴포넌트 선언: `function` 키워드 우선 (arrow function export default 지양)

```tsx
// Good
export default function UserCard({ name }: UserCardProps) {
  return <div>{name}</div>;
}

// Bad
const UserCard = ({ name }: UserCardProps) => <div>{name}</div>;
export default UserCard;
```

---

## 3. Props

- camelCase 사용; React 컴포넌트 타입의 prop은 PascalCase
- boolean prop은 명시적 `true` 생략

```tsx
<Button disabled />          // Good
<Button disabled={true} />   // Bad
```

- `<img>` 에는 항상 `alt` 필수; "picture of", "image of" 등 중복 표현 금지
- `key` prop에 배열 인덱스 금지 → 안정적인 ID 사용
- spread props 최소화; 불필요한 prop 걸러낸 후 전달

---

## 4. TypeScript

- 모든 Props에 interface 또는 type 정의 필수

```tsx
interface UserCardProps {
  id: number;
  name: string;
  role?: string;
}
```

- `any` 금지; 불명확할 때는 `unknown` 사용
- API 응답 타입은 별도 `types/` 폴더에서 관리

```ts
// types/auth.ts
export interface AuthResponse {
  success: boolean;
  data: { token: string };
  message: string;
  code: string;
}
```

---

## 5. JSX 포맷

- JSX 속성: 큰따옴표(`"`), JS 문자열: 작은따옴표(`'`)
- 자식 없는 태그: 반드시 self-close
- 멀티라인 JSX는 괄호로 감싸기
- 중괄호 안쪽 공백 없음: `{foo}` (not `{ foo }`)
- self-closing 태그 슬래시 앞 공백 하나: `<Foo />`

```tsx
// Good
return (
  <UserCard
    id={user.id}
    name={user.name}
  />
);
```

---

## 6. 훅 사용 규칙

- 훅은 컴포넌트 최상단에서만 호출 (조건문/반복문 내 금지)
- 커스텀 훅으로 로직 재사용; 컴포넌트는 UI만 담당

```ts
// hooks/useAuth.ts
export function useAuth() {
  const token = useAuthStore((s) => s.token);
  const { data } = useQuery({ queryKey: ['me'], queryFn: fetchMe });
  return { token, user: data };
}
```

---

## 7. 서버 상태 — React Query

- 서버 데이터 페칭은 전부 React Query 사용 (직접 `useEffect` + `useState` 금지)
- queryKey는 배열 형태, 의존 값 포함

```ts
useQuery({ queryKey: ['users', userId], queryFn: () => getUser(userId) });
useMutation({ mutationFn: createUser, onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }) });
```

---

## 8. 클라이언트 상태 — Zustand

- 전역 UI 상태만 Zustand 사용 (서버 데이터는 React Query)
- store 파일은 `store/` 폴더, 이름은 `useXxxStore.ts`

```ts
// store/useAuthStore.ts
interface AuthState {
  token: string | null;
  setToken: (token: string) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  setToken: (token) => set({ token }),
}));
```

---

## 9. HTTP 클라이언트 — Axios

- Axios 인스턴스를 공통으로 생성해 사용 (`api/client.ts`)
- 요청/응답 인터셉터에서 JWT 첨부 및 에러 공통 처리
- API 함수는 `api/` 폴더에서 도메인별로 분리

```ts
// api/auth.ts
export const login = (body: LoginRequest) =>
  client.post<AuthResponse>('/auth/login', body).then((r) => r.data);
```

---

## 10. 이벤트 핸들러

- 핸들러 네이밍: `handle` 접두사 (컴포넌트 내부), `on` 접두사 (prop 이름)

```tsx
function LoginForm({ onSuccess }: { onSuccess: () => void }) {
  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    // ...
    onSuccess();
  }
  return <form onSubmit={handleSubmit}>...</form>;
}
```

- 메서드 언더스코어 접두사 금지 (`_handleClick` X)

---

## 11. 폴더 구조 (Gate Service 표준)

```
src/
├── api/          # Axios 함수 (도메인별)
├── components/   # 공통 UI 컴포넌트
├── hooks/        # 커스텀 훅
├── pages/        # 라우트 단위 페이지
├── store/        # Zustand store
├── types/        # 공통 TypeScript 타입
└── utils/        # 순수 유틸 함수
```

---

## 12. 접근성

- `<img>` alt 필수
- ARIA role은 유효한 값만 사용 (추상 role 금지)
- `accessKey` 사용 금지
