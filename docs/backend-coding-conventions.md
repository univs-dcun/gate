# Backend Coding Conventions

Google Java Style Guide 기반, Gate Service 프로젝트 특화 규칙 포함.

---

## 1. 네이밍

| 대상 | 규칙 | 예시 |
|------|------|------|
| 패키지 | lowercase, 언더스코어 없음 | `com.gate.auth` |
| 클래스 | UpperCamelCase, 명사형 | `UserService`, `AuthController` |
| 메서드 | lowerCamelCase, 동사형 | `findById`, `issueToken` |
| 상수 | UPPER_SNAKE_CASE (static final + 불변) | `MAX_RETRY_COUNT` |
| 필드/변수/파라미터 | lowerCamelCase | `userId`, `pageSize` |
- 접두/접미사 금지: `mName`, `name_`, `s_name` 등

---

## 2. 포맷

- **들여쓰기**: 스페이스 2칸 (탭 금지)
- **줄 길이**: 100자 제한 (URL, import 예외)
- **중괄호**: K&R 스타일 — 여는 중괄호 같은 줄, 닫는 중괄호 새 줄
- **빈 블록**: `{}` 허용 (단, `if/else`, `try/catch` 연쇄 구조에선 금지)
- **한 줄에 한 문장**
- **변수 선언**: 사용 지점 최대한 가까이, 한 줄에 하나

```java
// Good
if (condition) {
  doSomething();
}

// Bad
if (condition) doSomething();
```

---

## 3. Import

- 와일드카드 import 금지 (`import java.util.*` X)
- 순서: static import → 빈 줄 → non-static import (각 그룹 내 ASCII 정렬)
- 줄 바꿈 없음 (100자 제한 미적용)

---

## 4. 선언 & 수정자 순서

```
public protected private abstract default static final sealed transient volatile synchronized native strictfp
```

---

## 5. 어노테이션

- 클래스/메서드: 각각 한 줄
- 파라미터 없는 단일 어노테이션은 시그니처와 같은 줄 가능
- `@Override`: 합법적인 모든 경우에 필수 표기

---

## 6. 예외 처리

- 빈 catch 블록 금지 — 반드시 로그, 재던지기, 또는 주석으로 이유 명시
- 프로젝트 공통: 모든 예외는 `GlobalExceptionHandler`에서 중앙 처리
- 커스텀 예외는 의미 있는 이름 사용 (`AuthenticationFailedException`)

---

## 7. 패키지 구조 (Gate Service 표준)

```
com.gate.<service-name>
├── controller      # API 진입점, 요청/응답 변환만
├── usecase         # 비즈니스 로직 (+ service 하위 분리 가능)
├── repository      # DB 접근
├── domain          # Entity, VO
└── dto             # Request/Response DTO
```

---

## 8. DTO 규칙

- 일반 DTO: **record** 사용
  ```java
  public record UserResponse(Long id, String name) {}
  ```
- OpenFeign 용 DTO: **class** 사용 (역직렬화 이슈)
  ```java
  public class FeignUserDto { ... }
  ```

---

## 9. API 응답 포맷

```json
// 단건/목록
{ "success": true, "data": { ... }, "message": "OK", "code": "SUCCESS" }

// 페이징
{ "success": true, "data": { "content": [...], "page": 0, "size": 20, "totalElements": 100 }, "message": "OK", "code": "SUCCESS" }
```

---

## 10. Javadoc

- 모든 public 클래스·멤버에 작성
- 첫 문장은 명사/동사구 (완전한 문장 X)
- 블록 태그 순서: `@param` → `@return` → `@throws` → `@deprecated`
- 자명한 getter/setter, `@Override` 메서드는 생략 가능

---

## 11. 기타 규칙

- 배열 선언: `String[] args` (C스타일 `String args[]` 금지)
- long 리터럴: 대문자 `L` (`100L`)
- static 멤버: 클래스명으로 접근 (`MyClass.staticMethod()`)
- `Object.finalize()` 오버라이드 금지
- switch: `default` 레이블 필수; 신규 코드는 arrow(`->`) 방식 권장
