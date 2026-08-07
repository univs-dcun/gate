# 온프레미스 오라클 설치 가이드

온프레미스 납품에서 백엔드를 오라클로 기동할 때 **DB 쪽에서 먼저 준비되어야 하는 것들**을 적는다.
애플리케이션 배포 절차가 아니라 DBA 에게 요청할 항목과 설치 담당자가 채울 값의 목록에 가깝다.

관련 티켓: UG-296

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
| `univs_gate` | gate-service | V1 ~ V22 | `gate` |
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

## 4. 접속 정보를 어디에 넣는가 — `.env` 가 정답이다

여기가 함정이다. gate-config 만 고치면 **아무것도 바뀌지 않는다.**

서비스는 `spring.config.import: optional:configserver:…` 로 설정을 읽는다. 레거시 bootstrap 이
아니라 **config-data** 경로이고 (`spring-cloud-starter-bootstrap` 없음, `bootstrap.yml` 없음),
이 경로에서 config-server 프로퍼티는 **OS 환경변수보다 아래**다. `compose.*.yml` 이
`SPRING_DATASOURCE_USERNAME` 을 환경변수로 넘기는 한 그쪽이 이긴다.

> 근거: gate-config 의 `application-postgresql.yml` 은 지금 `url: url` / `username: username`
> 이라는 리터럴 자리표시자를 담고 있는데 dev·stage·prod 가 정상 기동한다.

### 채울 곳

각 서버의 `WORKING_DIR` 에 있는 **`.env`** 에 서비스별 변수를 넣는다.

```bash
# 오라클 설치 — 서비스마다 계정이 다르다
GATE_DB_URL=jdbc:oracle:thin:@<host>:1521/<service_name>
GATE_DB_USERNAME=univs_gate
GATE_DB_PASSWORD=<비밀번호>

FACE_DB_URL=jdbc:oracle:thin:@<host>:1521/<service_name>
FACE_DB_USERNAME=univs_face
FACE_DB_PASSWORD=<비밀번호>

PALM_DB_URL=…    PALM_DB_USERNAME=univs_palm     PALM_DB_PASSWORD=…
MATCH_DB_URL=…   MATCH_DB_USERNAME=univs_match   MATCH_DB_PASSWORD=…
AUTH_DB_URL=…    AUTH_DB_USERNAME=univs_auth     AUTH_DB_PASSWORD=…
```

`compose.*.yml` 은 이렇게 읽는다.

```yaml
SPRING_DATASOURCE_URL: ${GATE_DB_URL:-${CORE_DB_URL}/gate}
SPRING_DATASOURCE_USERNAME: ${GATE_DB_USERNAME:-${CORE_DB_USERNAME}}
SPRING_DATASOURCE_PASSWORD: ${GATE_DB_PASSWORD:-${CORE_DB_PASSWORD}}
```

**`{서비스}_DB_*` 를 안 넣으면 기존 `CORE_DB_*` 로 떨어진다.** PostgreSQL 환경(dev/stage/prod)은
`CORE_DB_*` 만 쓰므로 동작이 그대로다. `docker compose config` 로 양쪽을 확인했다.

`CORE_DB_URL` 은 `${CORE_DB_URL}/gate` 처럼 뒤에 DB 이름을 붙이는 PostgreSQL 형태다.
**오라클 URL 에는 이 형태를 쓸 수 없으므로 `{서비스}_DB_URL` 로 전체 URL 을 준다.**

### gate-config 는 무엇을 하나

`{서비스}-oracle.yml` 다섯 개가 같은 값을 갖고 있다. 환경변수가 우선하므로 평소에는 쓰이지
않지만, 환경변수를 주지 않는 구성(로컬 실행 등)에서 폴백으로 동작하고 무엇보다 **의도를
기록**한다. 두 곳의 계정 이름은 항상 같게 유지한다.

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

---

## 6. 설치 후 검증

```sql
-- 1) 서비스마다 자기 이력 테이블을 갖고 있는가 (계정별로 접속해서 확인)
SELECT installed_rank, version, description, success
FROM flyway_schema_history ORDER BY installed_rank;

-- 2) 마지막 버전이 기대와 맞는가
--    univs_gate=22, univs_face=1, univs_palm=1, univs_match=3, univs_auth=3

-- 3) 실패한 마이그레이션이 없는가
SELECT * FROM flyway_schema_history WHERE success = 0;

-- 4) match 계정에서 매칭 함수가 이름만으로 보이는가  ← §3
SELECT vlmatch(HEXTORAW('00'), HEXTORAW('00'), 60) FROM dual;
```

4번은 `univs_match` 로 접속해서 실행해야 의미가 있다. 다른 계정에서 되는 것은 소용없다.

---

## 7. 아직 검증되지 않은 것

**실제 오라클 인스턴스에서 V1 ~ V22 를 끝까지 돌려 본 적이 없다.** UG-296 이 열려 있는 이유다.

지금까지 확인한 것은 여기까지다.

- `flyway-database-oracle` 이 다섯 서비스의 부트 jar 에 모두 들어간다
- 그 모듈이 없으면 오라클 URL 로 `Flyway.configure().load()` 가 실제로 실패하고, 있으면 통과한다
- 30개 마이그레이션 SQL(gate 22 · face 1 · palm 1 · match 3 · auth 3)에 19c 에서 못 도는 구문이
  없다 (정적 검토)
- 계정을 공유하면 두 번째 서비스가 checksum 불일치로 죽고, `baseline-on-migrate: true` 가 걸린
  비어 있지 않은 스키마에서는 조용히 앞쪽 마이그레이션을 건너뛴다 (둘 다 H2 로 재현)
- `.env` 에 `{서비스}_DB_*` 를 주면 계정이 갈리고, 안 주면 기존 동작 그대로다
  (`docker compose config` 로 확인)

확인하지 못한 것은 **SQL 이 실제 오라클에서 끝까지 도는지**다. 정적 검토는 타입 변환, 제약 조건
충돌, 권한 문제 같은 것을 다 잡지 못한다. `GRANT CREATE SEQUENCE` 가 IDENTITY 컬럼에 실제로
필요한지도 실측하지 못했다 (과다 부여라 무해하다).

오라클 19 인스턴스가 확보되면 빈 스키마 다섯 개에 다섯 서비스를 순서대로 올려 §6 의 검증 쿼리를
돌려야 한다.
