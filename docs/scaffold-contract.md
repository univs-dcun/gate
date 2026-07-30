# 스캐폴드 공개 계약 (Scaffold Contract)

> **버전**: 1.1.0 (2026-07-30)
> **상태**: 확정 — UG-243(auth→gate 결합 제거) dev 배포·검증 완료 시점의 코드 기준
> **관련 티켓**: UG-243(계약 신설), UG-245(본 문서)

## 목적과 적용 범위

이 문서는 **MSA 스캐폴드**(discovery-server, config-server, gateway-server + 선택 모듈 auth-service)와
**비즈니스 모듈**(gate, face, match, palm, notify 등 제품 서비스) 사이의 경계면 계약을 정의한다.

- 스캐폴드와 비즈니스 모듈은 이 문서에 적힌 계약만을 통해 상호작용한다.
- 아래 계약을 변경하는 작업은 **호환성 검토 + 이 문서의 버전 갱신**을 동반해야 한다.
  (레포 분리 이후에는 이 문서가 두 레포가 함께 지키는 유일한 기준선이 된다)
- 계약이 아닌 것: 각 서비스의 내부 구현, 비즈니스 모듈끼리의 API. 이 문서의 관리 대상이 아니다.

계약은 4개 영역으로 구성된다:

| # | 영역 | 요약 |
|---|---|---|
| 1 | [인증 계약](#1-인증-계약-gatewayauth) | token/validate API, X-Account-* 헤더 주입, internal 경로 차단 |
| 2 | [공통 응답 포맷](#2-공통-응답-포맷) | `ResponseApi{success, data, errors}` + 에러 코드 체계 |
| 3 | [서비스 디스커버리](#3-서비스-디스커버리eureka-계약) | `spring.application.name`이 곧 주소 체계 |
| 4 | [설정(config) 계약](#4-설정config-계약) | config-server 파일 네이밍과 진실 저장소 |
| 5 | [알림(notify) 계약](#5-알림notify-계약) | Redis pub/sub 채널과 WebSocket 중계 규칙 |

---

## 1. 인증 계약 (gateway↔auth)

### 1.1 토큰 검증 API

게이트웨이는 인증이 필요한 라우트에서 auth-service의 아래 API를 호출한다.
(gateway: `AuthClient`, auth: `TokenController`)

```
POST /api/v1/auth/token/validate
Content-Type: application/json
Accept-Language: (클라이언트 요청의 값을 그대로 전달, 선택)

{ "accessToken": "<JWT>" }
```

**응답** (HTTP 200, 공통 응답 포맷으로 래핑):

```json
{
  "success": true,
  "data": {
    "valid": true,
    "accountId": 123,
    "email": "user@example.com"
  },
  "errors": null
}
```

- 토큰이 유효하지 않으면 (만료·위조 포함) **HTTP 200 + `data.valid=false`** 로 응답한다.
  `accountId`, `email`은 null. 게이트웨이는 이를 401로 변환하여 클라이언트에 반환한다.
- 이 API는 게이트웨이 전용이다. 비즈니스 모듈이 직접 호출하지 않는다.

### 1.2 사용자 컨텍스트 헤더 주입

인증 필터(`AuthenticationFilter`)를 통과한 요청에 대해 게이트웨이는 downstream으로 다음 헤더를 주입한다:

| 헤더 | 값 | 규칙 |
|---|---|---|
| `X-Account-Id` | 토큰의 계정 ID | **항상 검증 결과로 덮어쓴다** (클라이언트가 보낸 값은 신뢰하지 않음) |
| `X-Account-Email` | 토큰의 이메일 | **항상 제거 후, 검증 결과에 값이 있을 때만 설정** |

**비즈니스 모듈이 지켜야 할 것**:

- 인증된 사용자 식별은 이 두 헤더로만 한다. 토큰을 직접 파싱하거나 auth-service를 호출하지 않는다.
  (gate의 `UserContextInterceptor` → `UserContext` ThreadLocal 패턴 참고)
- 이 헤더는 게이트웨이의 `AuthenticationFilter`가 적용된 라우트에서만 신뢰할 수 있다.
  필터가 없는 라우트(공개 API)에서는 값이 없거나 위조 가능하므로 사용자 식별에 쓰지 않는다.

### 1.3 내부 경로 차단

게이트웨이의 전역 필터(`InternalPathBlockFilter`, `HIGHEST_PRECEDENCE`)가
**`/api/**/internal/**`** 패턴의 외부 접근을 라우트 설정과 무관하게 **404**로 차단한다.
(경로 세그먼트의 matrix variable `;a=b` 우회 포함)

**비즈니스 모듈이 지켜야 할 것**: 서비스간 내부 전용 엔드포인트는
`/api/v{n}/{도메인}/internal/...` 경로 규칙을 따르면 자동으로 외부 노출이 차단된다.
별도의 게이트웨이 설정 없이 이 규칙만 지키면 된다.

### 1.4 인증 필터 적용 정책

- 라우트별 인증 여부는 게이트웨이 라우트 설정(`gateway-server-{env}.yml`)에서
  `AuthenticationFilter` 필터 유무로 결정된다.
- 인증 예외 경로(현행): `/api/v1/auth/**`(로그인·가입·토큰), `/api/v1/demo/**`, `/api/v1/file`,
  `/api/v1/messages/**`, `/ws/**`(웹소켓). 그 외 비즈니스 라우트는 기본적으로 필터를 적용한다.
- ⚠️ **알려진 이슈**: 온프레미스 라우트 설정(`gateway-server-onpremise.yml`)의 인증 필터 부재는
  별도 이슈로 관리한다 (UG-243에서 식별).

---

## 2. 공통 응답 포맷

모든 서비스의 REST API는 아래 래퍼로 응답한다 (각 서비스의 `shared/web/dto/ResponseApi`):

```json
// 성공
{ "success": true,  "data": { ... },  "errors": null }

// 실패
{ "success": false, "data": null, "errors": { "code": "AUTH-104", "type": "EXPIRATION_TOKEN", "message": "..." } }
```

- `errors.code`: `{도메인 프리픽스}-{번호}` 형식. `PJ-0xx`(공통 시스템), `PJ-1xx`(공통 입력),
  도메인별 프리픽스(`AUTH-`, `GATE-` 등)는 각 서비스의 `ErrorType` enum이 소유한다.
- `errors.type`: `ErrorType` enum 이름. `errors.message`: i18n 처리된 사용자 메시지
  (`Accept-Language` 헤더 기반, ko/en).
- 페이징 응답의 `data`는 `{content, page, size, totalElements}` 구조(`CustomPage`)를 따른다.
- 예외는 각 서비스의 `GlobalExceptionHandler`가 중앙 처리하여 이 포맷으로 변환한다.

**계약인 이유**: 게이트웨이가 auth 응답을 이 포맷으로 언래핑하고(1.1), 프론트엔드·SDK가
서비스 구분 없이 단일 응답 처리 코드를 쓴다. 새 비즈니스 모듈도 이 포맷을 구현해야 스캐폴드에 꽂힐 수 있다.

---

## 3. 서비스 디스커버리(Eureka) 계약

`spring.application.name`이 시스템 전체의 주소 체계다. 게이트웨이 라우팅(`lb://{name}`),
서비스간 호출, config 파일 네이밍(§4)이 모두 이 이름을 키로 쓴다.

**현행 등록 이름**:

| 구분 | 서비스명 |
|---|---|
| 스캐폴드 core | `discovery-server`, `config-server`(Eureka 미등록, 고정 주소), `gateway-server` |
| 선택 모듈 | `auth-service` |
| 비즈니스(gate 제품) | `gate-service`, `face-service`, `match-server`, `palm-service`, `notify-service`, `fxp-preprocess-service` |

**규칙**:

- 네이밍: `{도메인}-service` (신규 서비스 기준. `match-server` 등 `-server` 접미사는 레거시 — 변경 시 라우트·config 파일명·compose가 함께 바뀌므로 계약 변경으로 취급)
- Eureka 서버 주소는 `http://discovery-server:8761/eureka` (docker 네트워크 기준), config-server는 `http://config-server:8888` 고정 주소로 접근한다.
- 서비스명 변경/추가/제거는 계약 변경이다: 게이트웨이 라우트, config 파일명, compose 서비스 정의가 연동된다.

---

## 4. 설정(config) 계약

### 4.1 진실 저장소

Spring 설정(yml)의 단일 진실은 **`univs-dcun/gate-config` 레포의 `main` 브랜치**다 (UG-233).
config-server가 이를 읽어 각 서비스에 제공한다. 온프레미스(native)는 이 레포의 클론을
`/config-repo` 볼륨으로 마운트한다.

### 4.2 파일 네이밍 규칙

| 파일 | 소유 | 용도 |
|---|---|---|
| `{spring.application.name}.yml` | 각 서비스 | 서비스 기본 설정 (예: `auth-service.yml`, `gate-service.yml`) |
| `{name}-{env}.yml` | 각 서비스 | 환경 오버라이드 (예: `gateway-server-dev.yml`, `-stag`, `-prod`) |
| `{name}-onpremise.yml` | 각 서비스 | 온프레미스 납품 변형 |
| `application-{profile}.yml` | 공통 | 전 서비스 공유 프로파일 (`-postgresql`, `-oracle`, `-prod`) |

- 각 서비스는 `spring.config.import: optional:configserver:http://config-server:8888` +
  `SPRING_PROFILES_ACTIVE`로 자기 설정을 가져온다. 프로파일 조합(예: `dev,postgresql`)이
  어떤 파일들을 합성하는지가 곧 설정 계약이다.
- **게이트웨이 라우트는 제품 지식이다**: `gateway-server-{env}.yml`의 routes 정의는
  스캐폴드가 아니라 제품(조합) 레이어 소유다. 레포 분리 시 이 파일은 스캐폴드 기본 config와
  제품 오버레이로 계층화한다 (compose 분해 단계에서 함께 다룸).

### 4.3 민감정보

DB 접속 정보, JWT_SECRET, OAuth 크리덴셜 등은 yml이 아니라 배포 환경의 `.env`
(compose environment) 로 주입한다. yml에는 `${ENV_VAR}` 참조만 둔다.
`JWT_SECRET`은 auth-service(발급)와 일부 서비스(자체 검증)가 공유하는 값이므로
스캐폴드 배포 단위에서 하나의 값으로 관리한다.

---

## 5. 알림(notify) 계약

notify-service는 범용 모듈이다 — 제품 도메인을 모르며, 제품과의 결합은 **Redis pub/sub 채널 계약**뿐이다.

동작: 제품 서비스가 Redis 채널에 JSON을 publish → notify가 구독하여 WebSocket
(`/topic/{prefix}/{routing-key값}`)으로 중계한다.

**채널 등록**: notify의 config(`notify-service.yml`)의 `notify.subscriptions`에 선언한다.

```yaml
notify:
  subscriptions:
    - channel: demo:result        # Redis 채널명
      routing-key: transactionUuid # payload JSON에서 라우팅 키로 쓸 필드
      topic-prefix: /topic/demo    # WebSocket 토픽 프리픽스
```

**현행 채널**:

| 채널 | routing-key | WebSocket 토픽 | 발행자 |
|---|---|---|---|
| `demo:result` | `transactionUuid` | `/topic/demo/{uuid}` | gate-service (데모 인증 결과, `DemoRedisPublisher`) |
| `plan:notify` | `accountId` | `/topic/plan/{accountId}` | **미확인** — 구독만 선언됨, 모노레포 내 발행 코드 없음 (정리 후보) |

**규칙**:

- payload는 JSON이어야 하고 routing-key로 선언된 필드를 반드시 포함해야 한다.
- 채널명 컨벤션: `{도메인}:{이벤트}`. 채널 추가는 config 수정만으로 가능(notify 코드 변경 불필요)
  — 하위 호환. 채널 제거·routing-key 변경은 비호환 변경이다.
- notify의 WebSocket 연결 인증은 JWT를 자체 검증한다 → `JWT_SECRET`을 auth와 공유한다
  (§4.3). 이 공유가 notify가 스캐폴드 배포 단위에 묶이는 이유다.

---

## 계약 변경 절차

1. 변경이 위 4개 영역에 해당하는지 확인한다. 해당하면 하위 호환 여부를 판단한다.
   - **하위 호환** (필드 추가, 새 선택 헤더 등): 마이너 버전 업. 예: X-Account-Email 추가(UG-243)
   - **비호환** (필드 제거·의미 변경, 경로 변경, 서비스명 변경): 메이저 버전 업 + 소비자 전수 조사
2. 이 문서를 같은 PR(또는 같은 작업 단위)에서 갱신하고 버전을 올린다.
3. 레포 분리 이후에는: 스캐폴드 릴리스 태그(예: `scaffold v1.2.0`)에 이 문서의 계약 버전을
   명시하고, 비즈니스 모듈은 호환 계약 버전을 선언한다.

## 변경 이력

| 계약 버전 | 날짜 | 내용 |
|---|---|---|
| 1.1.0 | 2026-07-30 | §5 알림(notify) Redis 채널 계약 추가 (UG-246, notify의 범용 모듈 승격) |
| 1.0.0 | 2026-07-30 | 최초 작성 (UG-245). UG-243으로 확정된 인증 계약 포함 |
