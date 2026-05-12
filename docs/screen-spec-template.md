# 화면 스펙 페이지 템플릿

> Notion에서 **기능당 1페이지**를 생성할 때 사용하는 표준 구조. 같은 기능에 대한 후속 작업은 새 페이지를 만들지 않고 이 페이지의 [작업 이력] 섹션을 업데이트한다.
> 위치: Gate Service > 화면 스펙 > {모듈명} > {화면명}

---

## {모듈명} / {화면명}

| 항목 | 내용 |
|------|------|
| Figma 링크 | {Figma URL or "스크린샷 첨부"} |
| 담당 서비스 | backend/{service-name} |
| 작성일 | {YYYY-MM-DD} |
| 상태 | 개발중 / 완료 / 이슈 |

---

## Figma 원본

{Figma 스크린샷 첨부 또는 Figma URL}

---

## API 계약서 (확정)

### {API 이름}
- **Method & Path**: `GET /api/v1/{resource}`
- **Query Parameters**:
  - `param1` (string, 선택): 설명
  - `page` (int, 기본값 0): 페이지 번호
  - `size` (int, 기본값 20): 페이지 크기
- **Request Body**: 없음 or
  ```json
  {
    "field1": "string (필수)",
    "field2": "string (선택)"
  }
  ```
- **Response**:
  ```json
  {
    "success": true,
    "data": {
      "content": [],
      "page": 0,
      "size": 20,
      "totalElements": 100
    },
    "message": null,
    "code": null
  }
  ```
- **Error Cases**:
  - `404` : 리소스 없음
  - `400` : 유효성 검증 실패

---

## 구현 파일

### Backend (`backend/{service}/`)
- `api/controller/{Name}Controller.java`
- `application/usecase/{Action}{Name}UseCase.java`
- `application/service/{Name}Service.java`
- `infrastructure/repository/{Name}Repository.java`
- `domain/entity/{Name}.java`
- `api/dto/{Name}Request.java`
- `api/dto/{Name}Response.java`

### Frontend (`frontend/src/`)
- `types/{name}.ts`
- `api/{name}Api.ts`
- `hooks/use{ActionName}.ts`
- `components/{module}/{Name}Component.tsx`
- `pages/{module}/{Name}Page.tsx`

### DevOps
- `infra/docker/docker-compose.yml` (변경 시만)
- `infra/k8s/{service}-deployment.yaml` (변경 시만)

---

## 예상 동작

{Figma 화면 기준으로 기능이 어떻게 동작해야 하는지 서술}

예시:
- 목록 진입 시 전체 데이터 페이징 조회 (기본 20건)
- name 검색: 부분 일치
- email 검색: 완전 일치
- 정렬: createdAt DESC (고정)
- 행 클릭 시 상세 페이지로 이동

---

## 작업 이력

> 기능 추가·수정·배포 등 모든 작업을 여기에 누적한다. 버그 수정은 아래 [이슈 이력]에 별도 기록.

| 날짜 | 작업 내용 | PR / 커밋 | 담당자 |
|------|-----------|-----------|--------|
| {YYYY-MM-DD} | 최초 구현 | #{PR번호} | - |

---

## 이슈 이력

> 버그·장애·기획 변경 발생 시 기록. 정상 작업은 위 [작업 이력]에 기록.

| 날짜 | 구분 | 증상 | 원인 | 수정 파일 | 수정 내용 |
|------|------|------|------|-----------|-----------|
| - | - | - | - | - | - |
