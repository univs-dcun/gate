# 온프레미스 오라클 설치 가이드

온프레미스 납품에서 백엔드를 오라클로 기동할 때 **DB 쪽에서 먼저 준비되어야 하는 것들**을 적는다.
애플리케이션 배포 절차가 아니라 DBA 에게 요청할 항목과 설치 담당자가 채울 값의 목록에 가깝다.

> **배포 구성(compose, `.env`, 설치 스크립트, 이미지 태그)은 `univs-dcun/onprem` 저장소를 본다** (UG-323).
> 이 문서는 **gate 저장소의 네 서비스(gate · face · match · palm)가 요구하는 계약**만 기술한다.
> auth · config · discovery · gateway 의 계약은 `univs-dcun/msa-scaffold` 의
> [`docs/onpremise-contract.md`](https://github.com/univs-dcun/msa-scaffold/blob/dev/docs/onpremise-contract.md) 이 단일 진실이다 (UMS-16).
>
> 아래 §4 의 표가 계약이다. **이 표가 바뀌는 커밋은 onprem 저장소에 알린다.**

관련 티켓: UG-296, UG-323

---

## 1. 대상 오라클 버전 — 19c

**19c 로 확정한다.** 하한이 셋이고 그중 가장 높은 것이 18 이다.

| 제약 | 하한 | 근거 |
|---|---|---|
| `GENERATED ALWAYS AS IDENTITY` | 12.1 | `V1__init.sql` 등 거의 모든 PK 가 이 문법을 쓴다 |
| 30바이트를 넘는 식별자 | **12.2** | `liveness_verifying_by_image_enabled`(35), `idx_biometric_feature_project_type`(34) |
| Flyway 무료(Community) 대역 | **18.0** | 아래 참고 |

`flyway-database-oracle` 의 `OracleDatabase.ensureSupported` 는 세 단계로 판정한다
(11.7.2 · 10.10.0 모두 같은 상수를 쓴다 — 바이트코드로 확인).

| 호출 | 조건 | 결과 |
|---|---|---|
| `ensureDatabaseIsRecentEnough("10")` | 10 미만 | **예외** — 기동 실패 |
| `…RecommendUpgradeToFlywayEdition("18.0", PREMIUM)` | 18.0 미만 | `LOG.info` 로 유료 에디션 권유. **예외는 던지지 않는다** |
| `recommendFlywayUpgradeIfNecessaryForMajorVersion("21.3")` | **메이저**가 21 보다 큼 (22+) | `LOG.warn` 으로 Flyway 업그레이드 권유 |

즉 경고 없이 도는 구간은 **18 ~ 21** 이고 19 는 그 안에 있다. 11g 는 식별자 30바이트 제한 때문에
마이그레이션을 다시 써야 하므로 대상에서 제외한다.

---

## 2. 서비스마다 오라클 계정을 따로 판다

**이 문서에서 제일 중요한 항목이다.**

오라클은 계정과 스키마가 1:1 이다. PostgreSQL 처럼 한 계정 아래에 서비스별 데이터베이스를 두는
구성이 불가능하다.

```
PostgreSQL   한 계정(postgres) → 데이터베이스 gate / faces / palm / match / auth
오라클        계정 = 스키마      → 서비스 수만큼 계정이 필요하다
```

한 계정을 나눠 쓰면 다섯 서비스가 **`flyway_schema_history` 한 개를 공유**하게 된다. 각 서비스는
자기 `V1__init.sql` 을 갖고 있으므로, 두 번째로 뜨는 서비스는 이렇게 죽는다.

```
FlywayValidateException: Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 1
 -> Applied to database : -1927550246
 -> Resolved locally    : 395284105
```

부팅 순서에 따라 어느 서비스가 죽는지만 달라질 뿐, 반드시 하나 이상 죽는다.

### 필요한 계정

| 계정 | 서비스 | 마이그레이션 | PostgreSQL 에서의 DB 이름 |
|---|---|---|---|
| `univs_gate` | gate-service | V1 ~ V29 | `gate` |
| `univs_face` | face-service | V1 | `faces` |
| `univs_palm` | palm-service | V1 | `palm` |
| `univs_match` | match-server | V1 ~ V3 | `match` |
| `univs_auth` | auth-service (msa-scaffold 레포) | V1 ~ V3 | `auth` |

### 생성 DDL

계정마다 아래를 반복한다. 테이블스페이스 이름은 고객사 표준을 따른다.

```sql
CREATE USER univs_gate IDENTIFIED BY "<비밀번호>"
    DEFAULT TABLESPACE users
    QUOTA UNLIMITED ON users;

GRANT CREATE SESSION  TO univs_gate;
GRANT CREATE TABLE    TO univs_gate;
GRANT CREATE SEQUENCE TO univs_gate;
GRANT CREATE SYNONYM  TO univs_gate;
```

- `CREATE SEQUENCE` 는 `GENERATED ALWAYS AS IDENTITY` 가 내부적으로 시퀀스를 만들기 때문에 필요하다.
- `CREATE SYNONYM` 은 §3 의 `vlmatch` 시노님을 설치 담당자가 직접 만들 수 있게 하기 위한 것이다.
  엄밀히는 `univs_match` 만 필요하지만, 계정마다 절차를 갈라 두면 실수하기 쉬워 다섯 개 모두에 준다.
- 12c 부터 `RESOURCE` 롤에 `UNLIMITED TABLESPACE` 가 빠졌으므로 **쿼터를 따로 준다.**
- Flyway 가 `flyway_schema_history` 를 만드는 것도 `CREATE TABLE` 로 커버된다.

### 서비스 간 GRANT

**테이블에 대해서는 교차 GRANT 가 필요 없다.** 확인한 바:
`@Table(schema=)` 0건, `@SecondaryTable`·`@Subselect`·`JdbcTemplate`·Flyway 콜백 0건,
엔티티 테이블 17개가 서비스 간 완전히 서로소, 서비스 간 접근은 Feign 뿐이다.

**예외는 `vlmatch` 하나다.** 테이블이 아니라 함수이고, 다음 절에서 따로 다룬다.

---

## 3. match-server 는 `vlmatch` 함수가 필요하다

match-server 의 1:1 · 1:N 매칭은 DB 함수 `vlmatch()` 를 직접 호출한다. **스키마 한정자 없이**
호출하므로 `univs_match` 스키마에서 이름만으로 찾을 수 있어야 한다.

```java
// OracleDescriptorCustomRepositoryImpl
SELECT vlmatch(:descriptorBody, :targetDescriptorBody, :version) FROM dual
```

**이 함수는 우리 마이그레이션이 만들지 않는다.** 레포 어디에도 정의가 없다
(`CREATE FUNCTION` 0건) — 매칭 라이브러리 쪽에서 DB 에 설치하는 물건이다.

계정을 나누면 이 부분이 깨질 수 있다. 기존에는 모두 `UNIVS` 로 붙었으므로 같은 스키마에 설치된
함수가 그냥 보였지만, `univs_match` 로 바뀌면 보이지 않는다.

```sql
-- 함수가 다른 스키마에 설치돼 있을 때 (DBA 가 실행)
GRANT EXECUTE ON <설치스키마>.vlmatch TO univs_match;

-- 시노님은 univs_match 계정으로 직접 만들 수 있다 (§2 에서 CREATE SYNONYM 을 줬다)
CREATE SYNONYM vlmatch FOR <설치스키마>.vlmatch;
```

**부팅은 이것 없이도 된다.** 매칭 요청이 들어올 때 ORA-00904 로 처음 터지므로 설치 검증 항목에
반드시 넣을 것 (§6-4).

---

## 4. 앱이 요구하는 것 — 환경변수 계약

**이 절이 계약이다.** `.env` 변수 이름이나 compose 의 폴백 로직은 여기에 적지 않는다 —
그것은 `onprem` 저장소가 정한다. 여기 적는 것은 **컨테이너가 실제로 읽는 이름**이다.

### 왜 환경변수인가 — gate-config 만 고치면 아무것도 안 바뀐다

서비스는 `spring.config.import: optional:configserver:…` 로 설정을 읽는다. 레거시 bootstrap 이
아니라 **config-data** 경로이고 (`spring-cloud-starter-bootstrap` 없음, `bootstrap.yml` 없음),
이 경로에서 config-server 프로퍼티는 **OS 환경변수보다 아래**다. 환경변수를 주면 그쪽이 이긴다.

> 근거: gate-config 의 `application-postgresql.yml` 은 지금 `url: url` / `username: username`
> 이라는 리터럴 자리표시자를 담고 있는데 dev·stage·prod 가 정상 기동한다.

### 네 서비스 공통

| 컨테이너 환경변수 | Spring 속성 | 필수 | 비고 |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | — | ✅ | 온프레미스 오라클: `prod, oracle, onpremise` |
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | ✅ | 오라클은 **전체 URL**. §2 참고 |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | ✅ | 서비스마다 **다른 계정** |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` | ✅ | |
| `MANAGEMENT_SERVER_PORT` | `management.server.port` | | actuator 분리 포트 |

✅ **`SPRING_DATASOURCE_*` 를 안 주면 기동이 실패한다 (UG-307, 2026-09-22).** 앱 소스의
`application-{postgresql,oracle}.yml` 은 세 값을 `${SPRING_DATASOURCE_URL}` 같은 **기본값 없는** 플레이스홀더로만
갖는다. 예전에는 사내 개발 서버 주소가 기본값으로 들어 있어 누락 시 그쪽으로 조용히 붙으려 했다. 지금 누락 시
동작은 상황에 따라 다르지만 어느 쪽이든 **접속 성공으로 이어지지 않는다**: config-server 가 없으면
"Could not resolve placeholder" 로, config-server 가 있으면 gate-config 의 `{서비스}-oracle.yml` /
`application-postgresql.yml` 이 갖는 값이 `url`·`password` 리터럴 자리표시자라 드라이버가 URL 을 거부하며 멈춘다.
compose 아래에서 `.env` 의 `CORE_DB_*` 가 빠지면 환경변수가 빈 문자열로 넘어와 접속 단계에서 실패한다.
gate-config 에는 비밀이 없다 — `username` 만 서비스 계정명이고 나머지는 자리표시자다.
각 서비스의 `DatasourceCredentialGuardTest` 가 소스에 접속 정보나 기본값 있는 플레이스홀더가 다시 들어오는 것을
빌드 단계에서 막는다.

### gate-service 추가

| 컨테이너 환경변수 | Spring 속성 | 필수 | 비고 |
|---|---|---|---|
| `SPRING_DATA_REDIS_HOST` / `_PORT` / `_PASSWORD` | `spring.data.redis.*` | ✅ | |
| `GATEWAY_URL` | `gateway.url` | ✅ | 폐쇄망 내부 주소 |
| `FILE_ENABLE_UPLOAD` | `file.enable.upload` | ✅ | |
| `FILE_ROOT_PATH` | `file.root-path` | ✅ | 컨테이너 내부 경로 |
| `FILE_SECRET_KEY` | `file.secret-key` | ✅ | 비밀값 |
| `FILE_ALGORITHM_WAY` / `FILE_ALGORITHM_MOD` | `file.algorithm.way` / `.mod` | ✅ | 예: `AES` / `AES/ECB/PKCS5Padding` |
| `FILE_API-ENDPOINT_GET` | `file.api-endpoint.get` | | ⚠️ 이 변수만 이름에 **하이픈**이 들어간다. Spring 의 legacy 환경변수 해석으로 동작하지만 셸에서 `export` 가 안 되므로, 형태를 바꾼다면 실제 컨테이너로 확인할 것 |

### palm-service / match-server 추가

| 서비스 | 컨테이너 환경변수 | 필수 | 비고 |
|---|---|---|---|
| palm | `PALM_MODULE_URL` | ✅ | 폐쇄망 내부 palm 모듈 주소. UG-307 부터 소스 기본값이 없어 안 주면 기동 실패 |
| match | `LICENSE_SERVER_HOST` | | 라이선스 서버 |

### 이 문서가 다루지 않는 것

| 항목 | 단일 진실 |
|---|---|
| `AUTH_*`, 최초 관리자 부트스트랩, `POST /api/v1/auth/admin/init` 계약 | **msa-scaffold** `docs/onpremise-contract.md` §5 |
| config / discovery / gateway 환경변수, `CONFIG_SERVER_PROFILE` | **msa-scaffold** `docs/onpremise-contract.md` §1~§4 |
| gateway 인증 실패 시 상태 코드 | **msa-scaffold** `docs/onpremise-contract.md` §4.2 (UMS-15) |
| `.env` 변수 이름, compose 폴백, 호스트 포트, 이미지 태그 | **onprem** |
| Spring 설정 yml 자체 | **gate-config** (UG-233) |

### gate-config 는 무엇을 하나

`{서비스}-oracle.yml` 이 같은 값을 갖고 있다. 환경변수가 우선하므로 평소에는 쓰이지 않지만,
환경변수를 주지 않는 구성(로컬 실행 등)에서 폴백으로 동작하고 무엇보다 **의도를 기록**한다.
두 곳의 계정 이름은 항상 같게 유지한다.

---

## 5. 첫 설치에서 Flyway 가 하는 일

오라클 프로파일에는 **`baseline-on-migrate` 를 주지 않는다.** `{서비스}-postgresql.yml` 에만 있다.

이 설정은 Flyway 도입 시점(UG-229)에 이미 테이블이 들어 있던 기존 PostgreSQL 스키마를 입양하기
위한 것이다. 오라클에는 그런 과거가 없다 — 항상 빈 스키마에 처음 설치한다.

| 상황 | 동작 |
|---|---|
| 빈 스키마 (정상) | V1 부터 끝까지 순서대로 실행 |
| 비어 있지 않은 스키마 | `Found non-empty schema without schema history table` 로 **즉시 실패** |

두 번째 줄이 의도한 동작이다. `baseline-on-migrate: true` 였다면 같은 상황에서
`baseline-version` 이하를 통째로 건너뛰고도 기동에 성공한다 — gate 기준 **V1~V21 이 실행되지
않은 채 초록**이고, 나중에 없는 테이블을 찾다가 런타임에 터진다. H2 로 재현해 확인했다.

### 설치를 재시도할 때

스키마를 비우고 시작한다. **`user_tables` 만 보면 부족하다** — Flyway 의 빈 스키마 판정은
리사이클빈을 포함한 객체 전체를 보므로, `PURGE` 없이 `DROP TABLE` 한 뒤에는 눈에 안 보이는
`BIN$…` 객체가 남아 실패한다.

```sql
-- 해당 계정으로 접속해서
PURGE RECYCLEBIN;
SELECT object_name, object_type FROM user_objects;   -- 비어 있어야 한다
```

계정을 통째로 다시 만드는 편이 확실하다.

```sql
DROP USER univs_gate CASCADE;   -- DBA 권한 필요
```

### 마이그레이션이 중간에 실패했을 때 (오라클 전용 함정)

**오라클은 DDL 이 암묵적 커밋을 일으킨다.** 그래서 한 마이그레이션 파일 안에 DML 과 DDL 이
같이 있으면, DML 은 커밋됐는데 DDL 만 실패하는 상태가 남을 수 있다. postgresql 은 DDL 이
트랜잭션 안에 들어가므로 통째로 롤백된다 — 이 함정은 오라클에만 있다.

그 상태가 되면 `flyway_schema_history` 에 `success = 0` 행이 남고, `validate-on-migrate`
기본값이 `true` 라 **이후 모든 기동이 "Detected failed migration" 으로 실패한다.**
compose 가 `restart: unless-stopped` 이므로 컨테이너는 크래시 루프에 들어간다.

```sql
-- 실패한 마이그레이션이 있는지 (계정별로 접속해서)
-- ⚠️ Flyway 는 오라클에 이력 테이블과 컬럼을 소문자 따옴표 식별자로 만든다.
--    따옴표 없이 쓰면 대문자로 해석돼 ORA-00942 가 난다 (2026-09-22 실측).
SELECT "installed_rank", "version", "description", "success"
  FROM "flyway_schema_history"
 WHERE "success" = 0;
```

복구는 둘 중 하나다.

1. **실패한 문장을 손으로 실행한 뒤 이력을 고친다.** 무엇이 실패했는지 로그로 확인하고 그
   문장만 SQL*Plus 로 실행한 다음, 위 행을 지우고 재기동한다.
   ```sql
   DELETE FROM "flyway_schema_history" WHERE "success" = 0;
   COMMIT;
   ```
2. **Flyway repair 를 돌린다.** 애플리케이션이 못 뜨는 상태이므로 Flyway CLI 가 필요하다.

UG-302 는 이 함정을 피하려고 정리(V23, DML)와 인덱스 생성(V24, DDL)을 **버전을 나눠**
넣었다. 그러면 V23 은 성공으로 기록되고 V24 만 재시도되므로, 재실행할 때 정리가 두 번
돌지 않는다. 새 마이그레이션을 쓸 때도 같은 원칙을 따르는 편이 안전하다 — **DML 과 DDL 을
한 파일에 섞지 않는다.**

UG-325 도 같은 규칙을 따른다 — `feature_history` 테이블 생성(V25, DDL)과 `match_history` 의
REGISTER 행 복사(V26, DML — `INSERT … SELECT` 한 문장)를 나눴다. V26 이 중간에 실패하면 이
절의 절차대로 `feature_history` 를 비운 뒤 재시도한다. 복사만 하고 원본은 지우지 않으므로
재시도해도 `match_history` 는 그대로다. UG-326 의 V27 은 그 복사가 끝난 뒤 `match_history` 의
REGISTER 원본을 지우는 DML 한 문장이며, 짝이 되는 `feature_history` 행이 있는 것만 지우므로 재시도해도 안전하다.

UG-328 의 V29 는 DDL 과 DML 이 한 파일이다 — 시퀀스·컬럼 추가(DDL), 기존 행 백필(MERGE, DML), PL/SQL 로
시퀀스 재시작, 그리고 `MODIFY (… NOT NULL)`. 오라클은 DDL 을 자동 커밋하므로 백필이 중간에 실패하면 컬럼과
시퀀스는 남은 채 Flyway 에 실패로 기록된다. 재시도 전에 `activity_seq` 컬럼·시퀀스·유니크 인덱스를 지우고
`flyway_schema_history` 의 실패 행을 정리한다 (이 절 앞부분의 절차). 백필 자체는 재실행해도 같은 결과다.
V29 는 **gate-service 를 모두 내린 뒤** 기동해 적용한다 — 오라클은 `ADD (activity_seq)` 가 자동 커밋되어
컬럼이 즉시 열리므로, 구버전 인스턴스가 백필(MERGE) 뒤 `MODIFY (… NOT NULL)` 전에 행을 넣으면 NULL 이
남아 ORA-02296 으로 실패한다. PostgreSQL 은 한 트랜잭션 안에서 락이 유지되어 이 창이 없다.

---

## 6. 설치 후 검증

```sql
-- 식별자는 반드시 따옴표로 감싼다 (Flyway 가 소문자 따옴표 식별자로 만든다 — 위 §5 참고)

-- 1) 서비스마다 자기 이력 테이블을 갖고 있는가 (계정별로 접속해서 확인)
SELECT "installed_rank", "version", "description", "success"
FROM "flyway_schema_history" ORDER BY "installed_rank";

-- 2) 마지막 버전이 기대와 맞는가
--    univs_gate=29, univs_face=1, univs_palm=1, univs_match=3, univs_auth=3

-- 3) 실패한 마이그레이션이 없는가
SELECT * FROM "flyway_schema_history" WHERE "success" = 0;

-- 4) match 계정에서 매칭 함수가 이름만으로 보이는가  ← §3
SELECT vlmatch(HEXTORAW('00'), HEXTORAW('00'), 60) FROM dual;
```

4번은 `univs_match` 로 접속해서 실행해야 의미가 있다. 다른 계정에서 되는 것은 소용없다.

---

## 7. 검증 결과 (2026-09-22, Oracle Free 23ai) — 그리고 아직 남은 것

**gate 저장소의 네 서비스 마이그레이션(gate V1~V29 · face V1 · match V1~V3 · palm V1, 34개)을 실제 오라클에서
빈 스키마 네 개에 끝까지 적용했다.** UG-296 의 마지막 조건이었다.

| 항목 | 결과 |
|---|---|
| 인스턴스 | `gvenzl/oracle-free:23-slim-faststart` (Oracle Database 23ai, 23.26.3) 컨테이너, PDB `FREEPDB1` |
| 계정 | §2 의 DDL 그대로 `univs_gate`·`univs_face`·`univs_match`·`univs_palm` (CREATE SESSION/TABLE/SEQUENCE/SYNONYM + `users` 쿼터) |
| 실행 방식 | 각 서비스의 **실제 `runtimeClasspath`**(flyway-core 11.7.2 · flyway-database-oracle 11.7.2 · ojdbc11)로 `Flyway.configure().locations("classpath:db/migration/oracle").load().migrate()` — 앱 기동 없음 |
| 결과 | 34/34 SUCCESS, `"success"=0` 행 0. 마지막 버전 gate 29 · face 1 · match 3 · palm 1 |
| 멱등성 | gate 를 한 번 더 돌리면 validate 통과 + 적용 0건 |
| V29 (빈 테이블) | `ACTIVITY_SEQ` last_number 1, `match_history`/`feature_history`.`activity_seq` **NOT NULL + DEFAULT `ACTIVITY_SEQ.NEXTVAL`**, 유니크 인덱스 2개 생성 확인 |
| `vlmatch` (§3, §6-4) | 설치하지 않았으므로 `univs_match` 에서 **ORA-00904** — 문서대로 부팅과 무관, 매칭 시점에 터지는 항목임을 확인 |
| 테이블 | gate 13개(+이력) — V21 뒤에도 `face_feature`·`palm_feature` 가 남는 것은 PostgreSQL 과 동일(두 방언 모두 DROP 하지 않음) |

**실측으로 드러나 문서를 고친 것**

- Flyway 는 오라클에 `"flyway_schema_history"` 와 그 컬럼을 **소문자 따옴표 식별자**로 만든다. §5·§6 의 쿼리를
  따옴표 없이 쓰면 `ORA-00942: table or view does not exist` 가 난다 — 이 문서의 쿼리를 전부 따옴표 형태로 바꿨다.
- §6-2 의 gate 기대 버전이 22 로 낡아 있었다 → 29.

**아직 검증되지 않은 것**

- **19c 실기.** 검증은 23ai 에서 했다. 23ai 는 19c 의 상위집합이라 "23 에서 되는데 19 에서 안 되는 구문"
  (`IF NOT EXISTS`, `BOOLEAN` 타입 등)이 있을 수 있는데, 그 계열은 `OracleMigrationSyntaxTest` 가 정적으로
  금지하고 있다. 둘을 합쳐 "실제 오라클에서 끝까지 돈다 + 19c 전용 금지 구문 없음" 까지가 지금의 근거다.
  고객사 19c 인스턴스가 확보되면 같은 절차(§2 계정 → 서비스 기동 → §6)를 한 번 더 돈다.
- **Hibernate 매핑 검증.** 앱을 오라클로 기동하지 않았으므로 엔티티 ↔ 컬럼 타입 정합(`ddl-auto: validate` 에
  해당하는 검사)은 미실측이다. 정적으로는 `DialectSchemaParityTest` 가 두 방언의 컬럼 이름·NULL 허용성을 대조한다.
- auth-service(msa-scaffold, V1~V3)는 이 저장소 범위 밖이라 돌리지 않았다.
- ojdbc11 버전이 gate·palm 은 23.7.0.25.01, face·match 는 21.9.0.0 으로 갈린다(Boot BOM 과 직접 선언의 차이).
  둘 다 동작했지만 통일 여부는 별건.
- `GRANT CREATE SEQUENCE` 가 IDENTITY 컬럼에 실제로 필요한지는 따로 떼어 보지 않았다 (준 상태에서 성공, 과다 부여라 무해).

재현 절차는 UG-296 코멘트(2026-09-22)에 있다 — 컨테이너 기동, 계정 DDL, runtimeClasspath 추출용 Gradle init
스크립트, 단일 파일 Flyway 실행기.
