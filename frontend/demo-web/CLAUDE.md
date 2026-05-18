# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
npm run dev      # dev server on port 5173
npm run build    # production build (also runs TypeScript check)
npm run lint     # ESLint
```

No test runner is configured.

## Environment

`.env.local` is required:
```
UNIVS_API_BASE_URL=https://develop.univs.ai:18090
NODE_TLS_REJECT_UNAUTHORIZED=0   # dev server uses self-signed cert
```

The dev server must run on **port 5173** — the backend has CORS configured for that origin only.

## Architecture

This is a Next.js 16 (App Router) demo app for the UNIVS face and vein authentication SDK.

### Feature areas

Two main sections, switchable via `BottomTabBar` (face / vein / settings tabs):

- **Face auth** (`/face-auth/*`, `/face-register/*`) — face registration, 1:1 verification, 1:N identification, liveness check, photo-based verification
- **Vein auth** (`/vein/*`) — palm-vein registration and authentication using device camera

### Authentication flow
- The user enters an API key on `/login`. It is stored in `localStorage` as `univs_api_key`.
- `ApiKeyContext` (`src/contexts/ApiKeyContext.tsx`) reads the key on mount, calls `fetchProjectInfo()`, and redirects unauthenticated users to `/login`.
- All pages consume `useApiKey()` to access `apiKey`, `projectName`, and `projectError`.

### API proxy

All browser API calls use relative paths (`/api/univs/...`). Two server-side handlers cover these:

1. **Catch-all route** `src/app/api/univs/[...path]/route.ts` — proxies POST and GET requests to `UNIVS_API_BASE_URL/api/v1/demo/[...path]`, forwarding query params and body. Handles all endpoints except `/config`.

2. **Dedicated config route** `src/app/api/univs/config/route.ts` — exists because the upstream `GET /config` requires a request body, which browser Fetch and Node.js built-in fetch reject. Uses the Node.js `https` module directly.

`next.config.ts` also declares a rewrite for `/api/univs/:path*`, but Next.js route handlers take precedence so it is effectively unused.

The API key is sent as a query param (`?apiKey=...`) on all requests via `postWithImage()` in `src/lib/api.ts`.

### Multipart field names

Using the wrong field name returns a `PJ-101` error from the upstream:

| Endpoint | Field name |
|---|---|
| `/user` (register), `/verify/image` | `faceImage` |
| `/liveness`, `/identify` | `matchingFaceImage` |
| `/verify/image` two-image variant | `matchingFaceImage` + `targetMatchingFaceImage` |

`requestFaceApi()` in `src/lib/api.ts` picks the correct field based on `MATCHING_ENDPOINTS`.

### Camera pages

`/face-auth/camera` and `/vein/auth/camera` are driven by URL query params:

| Param | Values | Effect |
|---|---|---|
| `mode` | `1:1` (default), `1n`, `liveness` | Skips user-select screen for `1n`/`liveness` |
| `name` | URL-encoded string | Pre-selected user name |
| `type` | `photo` | Activates photo-upload path |

Face detection in `/face-auth/camera` uses `@mediapipe/tasks-vision` loaded dynamically from the jsDelivr CDN (WASM) and Google Storage (model file). Both are fetched at runtime, not bundled.

### Project name display
On startup, `fetchProjectInfo()` calls `GET /api/univs/config` and stores the returned `data.projectName` in `localStorage` (`univs_project_name`) and dispatches a `univs_project_name_updated` CustomEvent. The home page (`src/app/page.tsx`) shows `projectName` as the dashboard title, or a red error block with `[error code] message` if the API call fails.

### localStorage keys

| Key | Content |
|---|---|
| `univs_api_key` | API key entered at login |
| `univs_project_name` | Cached project name |
| `univs_registered_users` | JSON array of `RegisteredUser` objects (local cache) |

### i18n
`src/lib/translations.ts` exports a `translations` object keyed by `"ko" | "en"`. Language state lives in `LanguageContext` (`src/contexts/LanguageContext.tsx`). All UI strings come from `tx = translations[lang]`.

### Styles
Tailwind CSS v4 with inline style overrides for dynamic values. Shared card styles are in `src/styles/cardStyles.ts`.
