# Matcher API Documentation

**Base URL:** `/api/v1/match`

All responses are wrapped in `ResponseApi<T>`:
```json
{
  "success": true,
  "message": "",
  "processCode": "1",
  "errorType": "",
  "data": { ... }
}
```

---

## 1. Register Descriptor with Client-provided Face ID

**POST** `/api/v1/match/face-id`

Register a user's facial descriptor using a client-provided faceId.

### Request Body
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| branchName | string | Yes | Branch name (1-255 chars) |
| faceId | string | Yes | Unique user identifier (1-255 chars) |
| descriptor | string | Yes | Base64-encoded facial descriptor |

```json
{
  "branchName": "tenant-1",
  "faceId": "user-123",
  "descriptor": "ZHAAADsAAACDjYWB..."
}
```

### Response
```json
{
  "success": true,
  "processCode": "1",
  "data": {
    "branchName": "tenant-1",
    "faceId": "user-123"
  }
}
```

### Errors
| ProcessType | Status | Description |
|-------------|--------|-------------|
| CLIENT_INPUT_ERROR | 400 | Invalid input |
| ALREADY_REGISTERED_USER | 400 | faceId already exists in branch |
| ALREADY_REGISTERED_DESCRIPTOR | 400 | Descriptor matches existing user (similarity >= 0.85) |

---

## 2. Register Descriptor with Server-generated Face ID

**POST** `/api/v1/match`

Register a user's facial descriptor. The server generates a UUID for faceId.

### Request Body
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| branchName | string | Yes | Branch name (1-255 chars) |
| descriptor | string | Yes | Base64-encoded facial descriptor |

```json
{
  "branchName": "tenant-1",
  "descriptor": "ZHAAADsAAACDjYWB..."
}
```

### Response
```json
{
  "success": true,
  "processCode": "1",
  "data": {
    "branchName": "tenant-1",
    "faceId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

### Errors
| ProcessType | Status | Description |
|-------------|--------|-------------|
| CLIENT_INPUT_ERROR | 400 | Invalid input |
| ALREADY_REGISTERED_USER | 400 | faceId already exists |
| ALREADY_REGISTERED_DESCRIPTOR | 400 | Descriptor matches existing user |

---

## 3. Update Descriptor

**PUT** `/api/v1/match`

Update an existing user's facial descriptor.

### Request Body
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| branchName | string | Yes | Branch name (1-255 chars) |
| faceId | string | Yes | Existing user identifier (1-255 chars) |
| descriptor | string | Yes | New Base64-encoded facial descriptor |

```json
{
  "branchName": "tenant-1",
  "faceId": "user-123",
  "descriptor": "ZHAAADsAAACDjYWB..."
}
```

### Response
```json
{
  "success": true,
  "processCode": "1",
  "data": {
    "branchName": "tenant-1",
    "faceId": "user-123"
  }
}
```

### Errors
| ProcessType | Status | Description |
|-------------|--------|-------------|
| CLIENT_INPUT_ERROR | 400 | Invalid input |
| EMPTY_GALLERY | 400 | Branch does not exist |
| INVALID_FACE_ID | 400 | faceId not found in branch |
| ALREADY_REGISTERED_DESCRIPTOR | 400 | New descriptor matches another user |

---

## 4. Delete Descriptor

**DELETE** `/api/v1/match/{branchName}/{faceId}`

Delete a user's facial descriptor.

### Path Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| branchName | string | Branch name |
| faceId | string | User identifier |

### Response
```json
{
  "success": true,
  "processCode": "1",
  "data": {
    "branchName": "tenant-1",
    "faceId": "user-123"
  }
}
```

### Errors
| ProcessType | Status | Description |
|-------------|--------|-------------|
| CLIENT_INPUT_ERROR | 400 | Invalid input |
| EMPTY_GALLERY | 400 | Branch does not exist |
| INVALID_FACE_ID | 400 | faceId not found in branch |

---

## 5. Verify by ID (1:1 Matching)

**POST** `/api/v1/match/verify/id`

Compare a descriptor against a registered user's descriptor by faceId.

### Request Body
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| branchName | string | Yes | Branch name (1-255 chars) |
| faceId | string | Yes | Target user identifier (1-255 chars) |
| descriptor | string | Yes | Base64-encoded descriptor to compare |

```json
{
  "branchName": "tenant-1",
  "faceId": "user-123",
  "descriptor": "ZHAAADsAAACDjYWB..."
}
```

### Response
```json
{
  "success": true,
  "processCode": "1",
  "data": {
    "similarity": "0.95432"
  }
}
```

### Errors
| ProcessType | Status | Description |
|-------------|--------|-------------|
| CLIENT_INPUT_ERROR | 400 | Invalid input |
| EMPTY_GALLERY | 400 | Branch does not exist |
| INVALID_FACE_ID | 400 | faceId not found in branch |
| DIFFERENT_EXTRACTION_TYPE | 400 | Descriptor versions do not match |

---

## 6. Verify by Descriptor (1:1 Matching)

**POST** `/api/v1/match/verify/descriptor`

Compare two descriptors directly without database lookup.

### Request Body
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| descriptor | string | Yes | Base64-encoded source descriptor |
| targetDescriptor | string | Yes | Base64-encoded target descriptor |

```json
{
  "descriptor": "ZHAAADsAAACDjYWB...",
  "targetDescriptor": "ZHAAADwAAACFgYSC..."
}
```

### Response
```json
{
  "success": true,
  "processCode": "1",
  "data": {
    "similarity": "0.87654"
  }
}
```

### Errors
| ProcessType | Status | Description |
|-------------|--------|-------------|
| CLIENT_INPUT_ERROR | 400 | Invalid input |

---

## 7. Identify (1:N Matching)

**POST** `/api/v1/match/identify`

Find the closest matching user in a branch.

### Request Body
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| branchName | string | Yes | Branch name (1-255 chars) |
| descriptor | string | Yes | Base64-encoded descriptor to search |

```json
{
  "branchName": "tenant-1",
  "descriptor": "ZHAAADsAAACDjYWB..."
}
```

### Response
```json
{
  "success": true,
  "processCode": "1",
  "data": {
    "faceId": "user-456",
    "similarity": "0.92345"
  }
}
```

### Errors
| ProcessType | Status | Description |
|-------------|--------|-------------|
| CLIENT_INPUT_ERROR | 400 | Invalid input |
| EMPTY_GALLERY | 400 | Branch does not exist or has no matching users |

---

## Notes

- **Descriptor format**: Base64-encoded binary (first 8 bytes = type/version metadata, remaining 512 bytes = feature vector)
- **Similarity**: Returned as string with 5 decimal places (e.g., "0.95432")
- **Duplicate detection**: Registration/update fails if similarity >= 0.85 with existing descriptor
