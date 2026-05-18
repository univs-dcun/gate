export class ApiError extends Error {
  constructor(
    message: string,
    public readonly type: "network" | "unauthorized" | "server" | "unknown"
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export function getStoredApiKey(): string {
  if (typeof window === "undefined") return "";
  return localStorage.getItem("univs_api_key") ?? "";
}

function dataUrlToBlob(dataUrl: string): Blob {
  const [header, data] = dataUrl.split(",");
  const mime = header.match(/:(.*?);/)?.[1] ?? "image/jpeg";
  const binary = atob(data);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return new Blob([bytes], { type: mime });
}

async function postWithImage(
  path: string,
  imageDataUrl: string,
  extraQuery?: Record<string, string>,
  fieldName = "faceImage"
) {
  const apiKey = getStoredApiKey();
  const form = new FormData();
  form.append(fieldName, dataUrlToBlob(imageDataUrl), "face.jpg");

  const qs = new URLSearchParams({ apiKey });
  if (extraQuery) {
    for (const [k, v] of Object.entries(extraQuery)) qs.set(k, v);
  }

  let res: Response;
  try {
    res = await fetch(`/api/univs${path}?${qs.toString()}`, {
      method: "POST",
      body: form,
    });
  } catch {
    throw new ApiError("network error", "network");
  }

  if (res.status === 401 || res.status === 403) {
    throw new ApiError("unauthorized", "unauthorized");
  }
  if (res.status >= 500) {
    throw new ApiError("server error", "server");
  }

  const json = await res.json().catch(() => null);
  const projectName: string | undefined = json?.data?.projectName ?? json?.projectName;
  if (projectName) {
    window.dispatchEvent(new CustomEvent("univs_project_name_updated", { detail: projectName }));
  }
  return json;
}

const MATCHING_ENDPOINTS = ["/liveness", "/identify", "/verify/image", "/verify"];

export async function verifyByImages(targetImageDataUrl: string, liveImageDataUrl: string) {
  const apiKey = getStoredApiKey();
  const form = new FormData();
  form.append("matchingFaceImage", dataUrlToBlob(targetImageDataUrl), "target.jpg");
  form.append("targetMatchingFaceImage", dataUrlToBlob(liveImageDataUrl), "face.jpg");

  const qs = new URLSearchParams({ apiKey });

  let res: Response;
  try {
    res = await fetch(`/api/univs/verify/image?${qs.toString()}`, {
      method: "POST",
      body: form,
    });
  } catch {
    throw new ApiError("network error", "network");
  }

  if (res.status === 401 || res.status === 403) throw new ApiError("unauthorized", "unauthorized");
  if (res.status >= 500) throw new ApiError("server error", "server");

  const json = await res.json().catch(() => null);
  const projectName: string | undefined = json?.data?.projectName ?? json?.projectName;
  if (projectName) {
    window.dispatchEvent(new CustomEvent("univs_project_name_updated", { detail: projectName }));
  }
  return json;
}

/** 공통 API 호출 함수 — endpoint: "/user" | "/verify/image" | "/identify" | "/liveness" 등 */
export function requestFaceApi(endpoint: string, imageDataUrl: string, extra?: Record<string, string>) {
  const fieldName = MATCHING_ENDPOINTS.includes(endpoint) ? "matchingFaceImage" : "faceImage";
  return postWithImage(endpoint, imageDataUrl, extra, fieldName);
}

export function registerFace(imageDataUrl: string, username?: string, userDescription?: string) {
  const extra: Record<string, string> = {};
  if (username) extra.username = username;
  if (userDescription) extra.userDescription = userDescription;
  return requestFaceApi("/user", imageDataUrl, Object.keys(extra).length ? extra : undefined);
}

export function verifyByImage(imageDataUrl: string) {
  return requestFaceApi("/verify/image", imageDataUrl);
}

export function identifyFace(imageDataUrl: string) {
  return requestFaceApi("/identify", imageDataUrl);
}

export function checkLiveness(imageDataUrl: string) {
  return requestFaceApi("/liveness", imageDataUrl);
}

// ── Registered users ──

export interface RegisteredUser {
  userId: string;
  faceId: string;
  username: string;
  userDescription: string;
  createdAt: string;
}

export async function fetchRegisteredUsers(): Promise<RegisteredUser[]> {
  const apiKey = getStoredApiKey();
  if (!apiKey) return [];
  try {
    const res = await fetch(`/api/univs/users?apiKey=${encodeURIComponent(apiKey)}`);
    if (!res.ok) return [];
    const json = await res.json().catch(() => null);
    const list = json?.data?.content ?? json?.data ?? [];
    if (!Array.isArray(list)) return [];
    return list.map((u: Record<string, string>) => ({
      userId: u.userId ?? u.faceId ?? "",
      faceId: u.faceId ?? u.userId ?? "",
      username: u.username ?? u.userDescription ?? "",
      userDescription: u.userDescription ?? "",
      createdAt: u.createdAt ?? "",
    }));
  } catch {
    return [];
  }
}

export type ProjectInfoResult =
  | { ok: true; name: string; livenessEnabled: boolean | null }
  | { ok: false; code: string; message: string };

export function cacheLivenessEnabled(value: boolean) {
  if (typeof window === "undefined") return;
  localStorage.setItem("univs_liveness_enabled", String(value));
  window.dispatchEvent(new CustomEvent("univs_liveness_updated", { detail: value }));
}

export function getStoredLivenessEnabled(): boolean | null {
  if (typeof window === "undefined") return null;
  const v = localStorage.getItem("univs_liveness_enabled");
  return v === null ? null : v === "true";
}

export async function fetchProjectInfo(): Promise<ProjectInfoResult> {
  const apiKey = getStoredApiKey();
  if (!apiKey) return { ok: false, code: "NO_KEY", message: "API Key가 없습니다." };
  try {
    const res = await fetch(`/api/univs/config`, {
      method: "GET",
      headers: {
        "accept": "*/*",
        "Accept-Language": "ko",
        "Accept-TimeZone": "Asia/Seoul",
        "X-Api-Key": apiKey,
        "Content-Type": "application/json",
      },
    });
    const json = await res.json().catch(() => null);
    if (!res.ok) {
      return {
        ok: false,
        code: json?.errors?.code ?? `HTTP ${res.status}`,
        message: json?.errors?.message ?? `서버 오류가 발생했습니다. (${res.status})`,
      };
    }
    const name: string | undefined = json?.data?.projectName;
    if (name) {
      const d = json?.data ?? {};
      const liveness =
        d.checkLiveness ?? d.livenessEnabled ?? d.liveness ?? d.useLiveness ??
        d.livenessCheck ?? d.enableLiveness ?? d.livenessDetectionEnabled ??
        d.livenessVerification ?? d.isLivenessEnabled ?? d.livenessActive;
      if (typeof liveness === "boolean") cacheLivenessEnabled(liveness);
      window.dispatchEvent(new CustomEvent("univs_project_name_updated", { detail: name }));
      return { ok: true, name, livenessEnabled: typeof liveness === "boolean" ? liveness : null };
    }
    return { ok: false, code: "NO_NAME", message: "프로젝트명을 찾을 수 없습니다." };
  } catch {
    return { ok: false, code: "NETWORK_ERROR", message: "네트워크 오류가 발생했습니다." };
  }
}
