# Notion 페이지 구조

## 전체 계층

```
Gate Service (홈)
├── 화면 스펙                       ← 기획-개발 추적 핵심
│   ├── 회원관리
│   │   ├── 회원 목록 조회
│   │   ├── 회원 상세
│   │   └── 회원 등록
│   ├── 인증
│   │   ├── 로그인
│   │   └── 비밀번호 재설정
│   └── {모듈명}
│       └── {화면명}               ← screen-spec-template.md 형식
│
├── 개발일지                        ← 작업 완료 후 자동 작성
│   └── YYYY-MM-DD {작업 요약}
│
└── API 문서                        ← 서비스별 전체 API 목록
    ├── auth-service
    ├── gateway
    └── {service-name}
```

## 화면 스펙 페이지 생성 규칙

- Claude Code가 화면 구현 완료 후 자동 생성
- 템플릿: docs/screen-spec-template.md
- 버그 발생 시 Claude Code가 [이슈 이력] 섹션 업데이트
- 기획 변경 시 [Figma 원본], [API 계약서], [이슈 이력] 동시 업데이트

## Notion MCP 설정

Claude Code에서 Notion을 사용하려면:
1. https://www.notion.so/profile/integrations 에서 Integration 생성
2. 워크스페이스에 Integration 연결
3. Claude Code settings에 MCP 서버 등록:

```json
{
  "mcpServers": {
    "notion": {
      "command": "npx",
      "args": ["-y", "@notionhq/notion-mcp-server"],
      "env": {
        "OPENAPI_MCP_HEADERS": "{\"Authorization\": \"Bearer {YOUR_TOKEN}\", \"Notion-Version\": \"2022-06-28\"}"
      }
    }
  }
}
```
