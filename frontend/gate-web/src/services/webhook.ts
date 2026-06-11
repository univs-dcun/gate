import httpClient from './http';

/* ──────────────────────────────────────────────
 * 웹훅 설정 — /api/v1/projects/{projectId}/webhook
 *  - GET    조회
 *  - PUT    저장
 *  - DELETE 삭제
 * ────────────────────────────────────────────── */

export interface WebhookConfig {
  webhookConfigId?: number;
  projectId?:       number;
  webhookUrl:       string;
  demoEnabled:      boolean;   // 데모 이벤트 웹훅 전송
  apiEnabled:       boolean;   // API 이벤트 웹훅 전송
}

interface ApiResponse<T> { success: boolean; data: T; errors: Record<string, string> | null }

/** 웹훅 설정 조회 (미설정 시 백엔드 응답에 따라 빈 값 처리) */
export const getWebhookConfig = (projectId: number) =>
  httpClient.get<ApiResponse<WebhookConfig>>(`/v1/projects/${projectId}/webhook`, {
    skipNetworkErrorRedirect: true,
  });

/** 웹훅 설정 저장 */
export const saveWebhookConfig = (
  projectId: number,
  body: { webhookUrl: string; demoEnabled: boolean; apiEnabled: boolean },
) =>
  httpClient.put<ApiResponse<WebhookConfig>>(`/v1/projects/${projectId}/webhook`, body);

/** 웹훅 설정 삭제 */
export const deleteWebhookConfig = (projectId: number) =>
  httpClient.delete<ApiResponse<null>>(`/v1/projects/${projectId}/webhook`);
