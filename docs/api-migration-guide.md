# API 변경 가이드 — `refactor/backend/gate/separate-face-palm-auth-media`

> **대상 독자**: 프론트엔드 엔지니어  
> **기준 브랜치**: `dev` → `refactor/backend/gate/separate-face-palm-auth-media`  
> **작성일**: 2026-06-05  
>
> 이 문서를 Claude Code에 컨텍스트로 제공하면 변경된 API에 맞춰 코드를 수정하는 데 도움이 됩니다.

---

## 요약

| 구분 | 내용 |
|---|---|
| 삭제된 API 그룹 | `/api/v1/users`, `/api/v1/sdk/*`, `/api/v1/match` (e-KYC 엔드포인트) |
| 신규 API 그룹 | `/api/v1/feature` (Face+Palm 통합), `/api/v1/feature/face/*`, `/api/v1/feature/face/match/*`, `/api/v1/feature/palm/*`, `/api/v1/match/palm/*` |
| 변경된 API | `/api/v1/dashboard/*` (파라미터 추가), `/api/v1/demo/*` (필드명 변경), `/api/v1/match` (이력 조회만 유지) |
| 주요 필드명 변경 | `faceImage` → `featureImage`, `matchingFaceImage` → `matchingFeatureImage`, `faceId` → `featureId`, `userId`·`userDescription` 삭제 |

---

## 1. 삭제된 API

### 1-1. 사용자(User) 모듈 전체 삭제

`/api/v1/users` 경로가 완전히 제거됐습니다. 아래 엔드포인트는 더 이상 존재하지 않습니다.

| 삭제된 엔드포인트 | 대체 엔드포인트 |
|---|---|
| `POST /api/v1/users` (사용자 등록) | `POST /api/v1/feature/face` |
| `PUT /api/v1/users` (사용자 수정) | `PUT /api/v1/feature/face/{faceFeatureId}` |
| `DELETE /api/v1/users/{userId}` | `DELETE /api/v1/feature/face/{faceFeatureId}` |
| `GET /api/v1/users/{userId}` | `GET /api/v1/feature/face/{faceFeatureId}` |
| `GET /api/v1/users/faceId/{faceId}` | `GET /api/v1/feature/face/faceId/{faceId}` |
| `GET /api/v1/users` (목록) | `GET /api/v1/feature/face` |

> **⚠️ 주의**: `userId` 식별자가 `faceFeatureId`로 대체됐습니다.

### 1-2. SDK 모듈 전체 삭제

`/api/v1/sdk/*` 경로 전체가 제거됐습니다. (내부 전용 기능이었으므로 별도 대체 없음)

### 1-3. `/api/v1/match` 내 e-KYC 엔드포인트 이동

기존 `/api/v1/match`에 있던 e-KYC 엔드포인트들이 `/api/v1/feature/face/match`로 이동됐습니다.  
**이력 조회** 엔드포인트만 `/api/v1/match`에 남아 있습니다.

| 삭제(이동)된 엔드포인트 | 새 위치 |
|---|---|
| `POST /api/v1/match/identify` | `POST /api/v1/feature/face/match/identify` |
| `POST /api/v1/match/verify/id` | `POST /api/v1/feature/face/match/verify/id` |
| `POST /api/v1/match/verify/image` | `POST /api/v1/feature/face/match/verify/image` |
| `POST /api/v1/match/liveness` | `POST /api/v1/feature/face/match/liveness` |
| `POST /api/v1/match/extract` | `POST /api/v1/feature/face/match/extract` |
| `POST /api/v1/match/verify/descriptor` | `POST /api/v1/feature/face/match/verify/descriptor` |

---

## 2. 신규 API

### 2-1. 얼굴 특징점 관리 — `GET /api/v1/feature/face`

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/api/v1/feature/face` | 얼굴 특징점 등록 |
| `PUT` | `/api/v1/feature/face/{faceFeatureId}` | 수정 |
| `DELETE` | `/api/v1/feature/face/{faceFeatureId}` | 삭제 |
| `GET` | `/api/v1/feature/face/{faceFeatureId}` | 단건 조회 |
| `GET` | `/api/v1/feature/face/faceId/{faceId}` | Face ID 기반 조회 |
| `GET` | `/api/v1/feature/face` | 목록 조회 |

**등록/수정 Request 필드** (`multipart/form-data`)

```
featureImage    File     얼굴 이미지 (등록 시 필수, 수정 시 선택)
description     String   설명
username        String   사용자 이름
transactionUuid String   요청 키
```

**Response 필드** (`FaceFeatureResponseDTO`)

```json
{
  "faceFeatureId": 1,
  "description": "홍길동",
  "username": "hong",
  "featureId": "face-ai-service-id",
  "featureImagePath": "/path/to/image.jpg",
  "checkLiveness": false,
  "createdAt": "2026-06-05T00:00:00",
  "transactionUuid": "uuid-string"
}
```

> `userId` → `faceFeatureId`, `faceId` → `featureId`, `faceImagePath` → `featureImagePath`

---

### 2-2. 얼굴 e-KYC 매칭 — `/api/v1/feature/face/match`

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/api/v1/feature/face/match/extract` | 특징점 추출 |
| `POST` | `/api/v1/feature/face/match/verify/id` | 얼굴 확인 (featureId 기반) |
| `POST` | `/api/v1/feature/face/match/verify/image` | 얼굴 확인 (image 기반) |
| `POST` | `/api/v1/feature/face/match/verify/descriptor` | 얼굴 확인 (특징점 기반) |
| `POST` | `/api/v1/feature/face/match/identify` | 얼굴 1:N 매칭 |
| `POST` | `/api/v1/feature/face/match/liveness` | 얼굴 라이브니스 |

**Request 필드명 변경** (기존 `/api/v1/match/*` 대비)

| 기존 필드명 | 변경 후 |
|---|---|
| `matchingFaceImage` | `matchingFeatureImage` |
| `faceImage` | `featureImage` |

---

### 2-3. 팜 특징점 관리 — `/api/v1/feature/palm`

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/api/v1/feature/palm` | 팜 등록 |
| `PUT` | `/api/v1/feature/palm/{palmFeatureId}` | 수정 |
| `DELETE` | `/api/v1/feature/palm/{palmFeatureId}` | 삭제 |
| `GET` | `/api/v1/feature/palm/{palmFeatureId}` | 단건 조회 |
| `GET` | `/api/v1/feature/palm` | 목록 조회 |

**등록 Request 필드** (`multipart/form-data`)

```
featureImage    File     팜 이미지 (필수)
description     String   설명
username        String   사용자 이름
transactionUuid String   요청 키
externalKey     String   외부 연결 키 (face ↔ palm 연결용, 선택)
```

**Response 필드** (`PalmFeatureResponseDTO`)

```json
{
  "palmFeatureId": 1,
  "description": "홍길동",
  "username": "hong",
  "featureId": "palm-ai-service-id",
  "featureImagePath": "/path/to/palm.jpg",
  "checkLiveness": false,
  "createdAt": "2026-06-05T00:00:00",
  "transactionUuid": "uuid-string"
}
```

---

### 2-4. 팜 e-KYC 매칭 — `/api/v1/match/palm`

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/api/v1/match/palm/identify` | 팜 1:N 매칭 |
| `POST` | `/api/v1/match/palm/liveness` | 팜 라이브니스 |

**PalmIdentify Request** (`multipart/form-data`)

```
featureImage    File     팜 이미지 (필수)
transactionUuid String   요청 키
```

**PalmIdentify Response**

```json
{
  "matchingHistoryId": 1,
  "palmFeatureId": 1,
  "featureId": "palm-ai-id",
  "success": true,
  "similarity": 95.23,
  "threshold": "0.8",
  "failureType": null,
  "failureReason": null,
  "matchingTime": "2026-06-05T00:00:00",
  "transactionUuid": "uuid"
}
```

---

### 2-5. Palm 데모 API — `/api/v1/demo/palm` (신규)

Face 데모와 동일한 구조로 Palm 전용 데모 엔드포인트가 추가됐습니다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/api/v1/demo/palm/user` | API Key 기반 팜 등록 |
| `GET` | `/api/v1/demo/palm/users` | API Key 기반 팜 목록 조회 |
| `POST` | `/api/v1/demo/palm/identify` | API Key 기반 팜 1:N 매칭 |
| `POST` | `/api/v1/demo/palm/liveness` | API Key 기반 팜 라이브니스 |

> 모든 엔드포인트는 인증 없이 호출 가능하며, 프로젝트의 **데모 활성화** 설정이 필요합니다.

#### `POST /api/v1/demo/palm/user` — 팜 등록

**Request** (`multipart/form-data`)

```
apiKey          String   API Key (필수)
featureImage    File     팜 이미지 (필수)
description     String   설명
username        String   사용자 이름
transactionUuid String   요청 키
```

**Response** (`PalmFeatureResponseDTO`)

```json
{
  "palmFeatureId": 1,
  "description": "홍길동",
  "username": "hong",
  "featureId": "palm-ai-service-id",
  "featureImagePath": "/path/to/palm.jpg",
  "checkLiveness": false,
  "createdAt": "2026-06-05T00:00:00",
  "transactionUuid": "uuid-string"
}
```

#### `GET /api/v1/demo/palm/users` — 팜 목록 조회

**Query Parameters** (Face `/demo/users`와 동일)

```
apiKey          String   API Key (필수)
userKeyword     String   검색 키워드
page            Integer  페이지 (기본값: 1)
pageSize        Integer  페이지 크기 (기본값: 10)
isDeleted       Boolean  삭제 여부
startDate       String   시작일 (yyyy-MM-dd)
endDate         String   종료일 (yyyy-MM-dd)
```

**Response** (`PalmFeaturesResponseDTO`)

```json
{
  "palmFeatures": [ { "palmFeatureId": 1, ... } ],
  "page": { ... }
}
```

#### `POST /api/v1/demo/palm/identify` — 팜 1:N 매칭

**Request** (`multipart/form-data`)

```
apiKey          String   API Key (필수)
featureImage    File     팜 이미지 (필수)
transactionUuid String   요청 키
```

**Response** (`PalmIdentifyResponseDTO`)

```json
{
  "matchingHistoryId": 1,
  "palmFeatureId": 1,
  "featureId": "palm-ai-id",
  "success": true,
  "similarity": 95.23,
  "threshold": "0.8",
  "failureType": null,
  "failureReason": null,
  "matchingTime": "2026-06-05T00:00:00",
  "transactionUuid": "uuid"
}
```

#### `POST /api/v1/demo/palm/liveness` — 팜 라이브니스

**Request** (`multipart/form-data`)

```
apiKey          String   API Key (필수)
featureImage    File     팜 이미지 (필수)
transactionUuid String   요청 키
```

**Response** (`PalmLivenessResponseDTO`)

```json
{
  "success": true,
  "score": 0.97,
  "threshold": 0.8,
  "failureReason": null,
  "transactionUuid": "uuid"
}
```

---

### 2-6. Face + Palm 통합 특징점 목록 — `GET /api/v1/feature`

Face와 Palm을 한 번에 조회하는 통합 엔드포인트입니다.

**Query Parameters**

```
featureType     FeatureType   FACE | PALM | ALL (기본값: ALL)
keyword         String        FID 또는 메모 검색
page            Integer       페이지 (기본값: 1)
pageSize        Integer       페이지 크기 (기본값: 10)
isDeleted       Boolean       삭제 여부
startDate       String        시작일 (yyyy-MM-dd)
endDate         String        종료일 (yyyy-MM-dd)
```

**Response**

```json
{
  "features": [
    {
      "featureType": "FACE",
      "featureId": 1,
      "description": "홍길동",
      "imageUrl": "https://...",
      "fid": "face-ai-service-id",
      "createdAt": "2026-06-05T00:00:00"
    }
  ],
  "page": { ... }
}
```

---

## 3. 변경된 API

### 3-1. 대시보드 — `featureType` 파라미터 추가

아래 엔드포인트 모두에 `featureType` 쿼리 파라미터가 추가됐습니다.

| 엔드포인트 | 변경 내용 |
|---|---|
| `GET /api/v1/dashboard/summary` | `featureType` 추가 (FACE\|PALM, 기본값 FACE) |
| `GET /api/v1/dashboard/trend` | `featureType` 추가 (FACE\|PALM, 기본값 FACE) |
| `GET /api/v1/dashboard/ratios` | `featureType` 추가 (FACE\|PALM, 기본값 FACE) |
| `GET /api/v1/dashboard/daily` | `featureType` 추가 (FACE\|PALM, 기본값 FACE) |

**기존 코드** (변경 전):
```
GET /api/v1/dashboard/summary?period=MONTH
```

**변경 후**:
```
GET /api/v1/dashboard/summary?period=MONTH&featureType=FACE
GET /api/v1/dashboard/summary?period=MONTH&featureType=PALM
```

> `featureType` 미전송 시 `FACE`로 동작합니다.

---

### 3-2. 매칭 이력 조회 — 응답 필드명 변경

`GET /api/v1/match`, `GET /api/v1/match/{transactionUuid}`

| 기존 필드명 | 변경 후 |
|---|---|
| `faceId` | `featureId` |
| `userId` | ❌ 제거 |
| `userDescription` | `description` |
| `faceImagePath` | `featureImagePath` |
| `matchingFaceImagePath` | `matchingFeatureImagePath` |

**변경 전 응답 (일부)**:
```json
{
  "faceId": "abc-face-id",
  "userId": 123,
  "userDescription": "홍길동",
  "faceImagePath": "/images/face.jpg",
  "matchingFaceImagePath": "/images/matching.jpg"
}
```

**변경 후 응답**:
```json
{
  "featureId": "abc-face-id",
  "description": "홍길동",
  "featureImagePath": "/images/face.jpg",
  "matchingFeatureImagePath": "/images/matching.jpg"
}
```

---

### 3-3. 데모 — 필드명 변경

#### `POST /api/v1/demo/user` (사용자 등록)

**Request 필드 변경** (`multipart/form-data`):

| 기존 | 변경 후 |
|---|---|
| `faceImage` | `featureImage` |
| `userDescription` | `description` |

**Response**: `UserResponseDTO` → `FaceFeatureResponseDTO`로 변경

```json
// 기존
{
  "userId": 1,
  "faceId": "abc",
  "userDescription": "홍길동",
  "faceImagePath": "/path/img.jpg"
}

// 변경 후
{
  "faceFeatureId": 1,
  "featureId": "abc",
  "description": "홍길동",
  "featureImagePath": "/path/img.jpg"
}
```

#### `POST /api/v1/demo/verify` (faceId 기반 확인)

**Request 필드 변경**:

| 기존 | 변경 후 |
|---|---|
| `matchingFaceImage` | `matchingFeatureImage` |

#### `POST /api/v1/demo/verify/image`

| 기존 | 변경 후 |
|---|---|
| `matchingFaceImage` | `matchingFeatureImage` |

#### `POST /api/v1/demo/identify`

| 기존 | 변경 후 |
|---|---|
| `matchingFaceImage` | `matchingFeatureImage` |

#### `POST /api/v1/demo/liveness`

| 기존 | 변경 후 |
|---|---|
| `matchingFaceImage` | `matchingFeatureImage` |

#### `GET /api/v1/demo/users` (사용자 목록)

**Response**: `UsersResponseDTO` → `FaceFeaturesResponseDTO`로 변경

```json
// 기존
{
  "users": [ { "userId": 1, ... } ],
  "page": { ... }
}

// 변경 후
{
  "faceFeatures": [ { "faceFeatureId": 1, ... } ],
  "page": { ... }
}
```

---

## 4. 주요 필드명 변경 요약표

프론트엔드 코드 전체 검색 시 참고하세요.

### Request 필드 (multipart form-data)

| 기존 필드명 | 새 필드명 | 영향 받는 엔드포인트 |
|---|---|---|
| `faceImage` | `featureImage` | `POST /demo/user`, `POST /feature/face`, `PUT /feature/face/*` |
| `matchingFaceImage` | `matchingFeatureImage` | `POST /demo/verify`, `POST /demo/verify/image`, `POST /demo/identify`, `POST /demo/liveness`, `POST /feature/face/match/*` |
| `userDescription` | `description` | `POST /demo/user` |

### Response 필드

| 기존 필드명 | 새 필드명 | 영향 받는 엔드포인트 |
|---|---|---|
| `userId` | `faceFeatureId` | `/demo/user`, `/demo/users` |
| `faceId` | `featureId` | `/match`, `/match/*`, `/demo/user` |
| `userId` (match history) | ❌ 제거 | `/match`, `/match/*` |
| `userDescription` | `description` | `/match`, `/match/*`, `/demo/user` |
| `faceImagePath` | `featureImagePath` | `/match`, `/match/*` |
| `matchingFaceImagePath` | `matchingFeatureImagePath` | `/match`, `/match/*` |
| `users` (배열) | `faceFeatures` (배열) | `GET /demo/users` |

### Query 파라미터

| 기존 | 새 파라미터 | 영향 받는 엔드포인트 |
|---|---|---|
| (없음) | `featureType` (FACE\|PALM) | `GET /dashboard/summary`, `/trend`, `/ratios`, `/daily` |

---

## 5. Claude Code 활용 팁

이 문서를 Claude Code 세션에서 활용할 때:

```
# 이 파일을 먼저 읽혀주세요
cat docs/api-migration-guide.md

# 예시 프롬프트:
"위 마이그레이션 가이드를 기반으로 현재 코드에서 /api/v1/match/identify 호출하는 부분을 
/api/v1/feature/face/match/identify 로 변경하고, matchingFaceImage 필드명을 
matchingFeatureImage 로 수정해줘"
```
