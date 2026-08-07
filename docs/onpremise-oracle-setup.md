# 온프레미스 오라클 설치 가이드

온프레미스 납품에서 백엔드를 오라클로 기동할 때 **DB 쪽에서 먼저 준비되어야 하는 것들**을 적는다.
애플리케이션 배포 절차가 아니라 DBA 에게 요청할 항목 목록에 가깝다.

관련 티켓: UG-296

---

## 1. 대상 오라클 버전 — 19c 이상

**19c 로 확정한다.** 세 가지 하한이 각각 다르고, 그중 가장 높은 것이 18 이다.

| 제약 | 하한 | 근거 |
|---|---|---|
| `GENERATED ALWAYS AS IDENTITY` | 12.1 | `V1__init.sql` 등 거의 모든 PK 가 이 문법을 쓴다 |
| 30바이트를 넘는 식별자 | **12.2** | `liveness_verifying_by_image_enabled`(35), `idx_biometric_feature_project_type`(34) |
| Flyway 무료(Community) 대역 | **18.0** | 아래 참고 |

Flyway 의 `flyway-database-oracle` 은 `OracleDatabase.ensureSupported` 에서 세 단계로 판정한다
(11.7.2 · 10.10.0 모두 동일).

```
ensureDatabaseIsRecentEnough("10")                                    → 10 미만이면 예외
ensureDatabaseNotOlderThan...RecommendUpgradeToFlywayEdition("18.0")  → 18 미만이면 유료 에디션 권유 (INFO 로그, 예외 아님)
recommendFlywayUpgradeIfNecessaryForMajorVersion("21.3")              → 21.3 초과면 Flyway 업그레이드 권유 (INFO)
```

**19 는 18.0 ~ 21.3 구간에 들어가 경고 한 줄 없이 통과하는 유일한 대역이다.**
11g 는 식별자 30바이트 제한 때문에 마이그레이션을 다시 써야 하므로 대상에서 제외한다.

---

## 2. 서비스마다 오라클 계정을 따로 판다

**이것이 이 문서에서 제일 중요한 항목이다.**

오라클은 계정과 스키마가 1:1 이다. PostgreSQL 처럼 한 계정 아래에 서비스별 데이터베이스를
두는 구성이 불가능하다.

```
PostgreSQL   한 계정(postgres) → 데이터베이스 gate / face / palm / faces
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

| 계정 | 서비스 | 마이그레이션 |
|---|---|---|
| `univs_gate` | gate-service | V1 ~ V22 |
| `univs_face` | face-service | V1 |
| `univs_palm` | palm-service | V1 |
| `univs_match` | match-server | V1 ~ V3 |
| `univs_auth` | auth-service (msa-scaffold 레포) | V1 ~ V3 |

### 생성 DDL

계정마다 아래를 반복한다. 테이블스페이스 이름은 고객사 표준을 따른다.

```sql
CREATE USER univs_gate IDENTIFIED BY "<비밀번호>"
    DEFAULT TABLESPACE users
    QUOTA UNLIMITED ON users;

GRANT CREATE SESSION  TO univs_gate;
GRANT CREATE TABLE    TO univs_gate;
GRANT CREATE SEQUENCE TO univs_gate;
```

- `CREATE SEQUENCE` 는 `GENERATED ALWAYS AS IDENTITY` 가 내부적으로 시퀀스를 만들기 때문에 필요하다.
- 12c 부터 `RESOURCE` 롤에 `UNLIMITED TABLESPACE` 가 빠졌으므로 **쿼터를 따로 준다.**
- Flyway 가 `flyway_schema_history` 를 만드는 것도 `CREATE TABLE` 로 커버된다.
- 서비스끼리 서로의 테이블을 읽지 않으므로 **교차 GRANT 는 필요 없다**
  (`@Table(schema=)` 0건, 다른 서비스 테이블을 참조하는 네이티브 쿼리 0건).

---

## 3. match-server 는 `vlmatch` 함수가 필요하다

match-server 의 1:1 · 1:N 매칭은 DB 함수 `vlmatch()` 를 직접 호출한다.

```java
// OracleDescriptorCustomRepositoryImpl
SELECT vlmatch(:descriptorBody, :targetDescriptorBody, :version) FROM dual
```

**이 함수는 우리 마이그레이션이 만들지 않는다.** 레포 어디에도 정의가 없다 — 매칭 라이브러리 쪽에서
DB 에 설치하는 물건이다. 따라서 설치 시점에 확인이 필요하다.

- `univs_match` 계정에서 `SELECT vlmatch(...) FROM dual` 이 도는지 확인한다.
- 함수가 다른 스키마에 설치돼 있다면 `GRANT EXECUTE` 와 시노님이 필요하다.

```sql
GRANT EXECUTE ON <설치스키마>.vlmatch TO univs_match;
CREATE SYNONYM univs_match.vlmatch FOR <설치스키마>.vlmatch;
```

부팅은 이것 없이도 되고 **매칭 요청이 들어올 때 처음 터진다.** 설치 검증 항목에 반드시 넣을 것.

---

## 4. gate-config 에 채울 값

설정의 단일 진실은 `univs-dcun/gate-config` 레포다. 납품 시 이 레포를 클론해
config-server 의 `/config-repo` 볼륨으로 마운트한다.

오라클 접속 정보는 **서비스별 파일**에 있다. 다섯 개를 각각 채운다.

```
gate-service-oracle.yml
face-service-oracle.yml
palm-service-oracle.yml
match-server-oracle.yml
auth-service-oracle.yml
```

각 파일의 자리표시자:

```yaml
spring:
  datasource:
    url: url            # jdbc:oracle:thin:@<host>:1521/<service_name>
    username: univs_gate
    password: password
```

공통값(드라이버, `db/migration/oracle` 위치, Hibernate 방언)은 `application-oracle.yml` 에 있으므로
건드리지 않는다.

---

## 5. 첫 설치에서 Flyway 가 하는 일

오라클 프로파일에는 **`baseline-on-migrate` 를 주지 않는다.** `{서비스}-postgresql.yml` 에만 있다.

이 설정은 Flyway 도입 시점(UG-229)에 이미 테이블이 들어 있던 기존 PostgreSQL 스키마를 입양하기
위한 것이다. 오라클에는 그런 과거가 없다 — 항상 빈 스키마에 처음 설치한다.

| 상황 | 동작 |
|---|---|
| 빈 스키마 (정상) | V1 부터 끝까지 순서대로 실행 |
| 비어 있지 않은 스키마 | `Found non-empty schema without schema history table` 로 **즉시 실패** |

두 번째 줄이 의도한 동작이다. `baseline-on-migrate: true` 였다면 같은 상황에서 앞쪽 마이그레이션을
통째로 건너뛰고도 기동에 성공해, 나중에 없는 테이블을 찾다가 런타임에 터진다. 설치 재시도나
DBA 가 미리 만들어 둔 객체가 있을 때 조용히 잘못되는 쪽보다 그 자리에서 멈추는 쪽이 낫다.

**따라서 설치를 재시도할 때는 스키마를 비우고 시작한다.**

```sql
-- 재시도 전, 해당 계정으로 접속해서
SELECT table_name FROM user_tables;   -- 비어 있어야 한다
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

-- 4) match 계정에서 매칭 함수가 도는가
SELECT vlmatch(HEXTORAW('00'), HEXTORAW('00'), 60) FROM dual;
```

---

## 7. 아직 검증되지 않은 것

**실제 오라클 인스턴스에서 V1 ~ V22 를 끝까지 돌려 본 적이 없다.** UG-296 이 열려 있는 이유다.

지금까지 확인한 것은 여기까지다.

- `flyway-database-oracle` 이 다섯 서비스의 부트 jar 에 모두 들어간다
- 그 모듈이 없으면 오라클 URL 로 `Flyway.configure().load()` 가 실제로 실패하고, 있으면 통과한다
- 27개 마이그레이션 SQL 에 19c 에서 못 도는 구문이 없다 (정적 검토)
- 계정을 공유하면 두 번째 서비스가 checksum 불일치로 죽는다 (H2 로 재현)

확인하지 못한 것은 **SQL 이 실제 오라클에서 끝까지 도는지**다. 정적 검토는 타입 변환, 제약 조건
충돌, 권한 문제 같은 것을 다 잡지 못한다. 오라클 19 인스턴스가 확보되면 빈 스키마 다섯 개에
다섯 서비스를 순서대로 올려 §6 의 검증 쿼리를 돌려야 한다.
