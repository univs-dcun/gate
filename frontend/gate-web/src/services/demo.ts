import axios from 'axios';
import httpClient from './http';

export interface QrCodeResponseDTO {
  base64QrCode: string;
  link: string;
}

interface ApiResponse<T> {
  success: boolean;
  data: T;
  errors: { code: string; type: string; message: string } | null;
}

/** 등록 QR코드 발급 */
export const getCreateUserQrCode = () =>
  httpClient.get<ApiResponse<QrCodeResponseDTO>>('/v1/demo/user/qr', {
    skipNetworkErrorRedirect: true,
  });

/* ─── 모바일 전용 Demo API ───────────────────────────────────
 * 인증 토큰 없이 apiKey를 body로 전송 — 별도 axios 인스턴스 사용
 * ──────────────────────────────────────────────────────────── */
const mobileClient = axios.create({
  baseURL: window.__APP_CONFIG__?.apiBaseUrl || import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
});

mobileClient.interceptors.request.use((config) => {
  config.headers['Accept-Language'] = localStorage.getItem('lang') ?? 'ko';
  return config;
});

export interface UserResponseDTO {
  userId: number;
  faceId: string;
  faceImagePath: string;
  createdAt: string;
}

export interface VerifyResponseDTO {
  success: boolean;
  faceId: string;
  similarity: number;
  failureType: string | null;
  failureReason: string | null;
  matchingFaceId: string;
  userDescription: string | null;
}

export interface IdentifyResponseDTO {
  success: boolean;
  faceId: string;
  similarity: number;
  failureType: string | null;
  failureReason: string | null;
  userDescription: string | null;
}

export interface LivenessResponseDTO {
  success:       boolean;
  transactionUuid: string;
  failureReason: string | null;
}

/** 등록 (POST /v1/demo/user) */
export const demoRegister = (apiKey: string, faceImage: Blob, userDescription?: string) => {
  const fd = new FormData();
  fd.append('apiKey', apiKey);
  fd.append('faceImage', faceImage, 'face.jpg');
  if (userDescription) fd.append('userDescription', userDescription);
  return mobileClient.post<ApiResponse<UserResponseDTO>>('/v1/demo/user', fd);
};

/** 1:1 확인 (POST /v1/demo/verify) */
export const demoVerify = (apiKey: string, faceId: string, matchingFaceImage: Blob, transactionUuid: string) => {
  const fd = new FormData();
  fd.append('apiKey', apiKey);
  fd.append('faceId', faceId);
  fd.append('matchingFaceImage', matchingFaceImage, 'face.jpg');
  fd.append('transactionUuid', transactionUuid);
  return mobileClient.post<ApiResponse<VerifyResponseDTO>>('/v1/demo/verify', fd);
};

/** 1:N 매칭 (POST /v1/demo/identify) */
export const demoIdentify = (apiKey: string, matchingFaceImage: Blob, transactionUuid: string) => {
  const fd = new FormData();
  fd.append('apiKey', apiKey);
  fd.append('matchingFaceImage', matchingFaceImage, 'face.jpg');
  fd.append('transactionUuid', transactionUuid);
  return mobileClient.post<ApiResponse<IdentifyResponseDTO>>('/v1/demo/identify', fd);
};

export interface SdkConfig {
  livenessRegisterEnabled:         boolean;
  livenessVerifyingByIdEnabled:    boolean;
  livenessVerifyingByImageEnabled: boolean;
  livenessIdentifyingEnabled:      boolean;
  demoEnabled?:                    boolean;
  sdkEnabled?:                     boolean;
  projectName?:                    string;
}

export interface SdkConfigByCode extends SdkConfig {
  projectName: string;
}

/** SDK 설정 조회 (GET /v1/sdk/config) — apiKey 기반 */
export const getSdkConfig = (apiKey: string) =>
  mobileClient.get<ApiResponse<SdkConfig>>('/v1/sdk/config', {
    headers: { 'X-Api-Key': apiKey },
  });

/** SDK 설정 조회 (GET /v1/sdk/config/{code}) — code(token) 기반 */
export const getSdkConfigByCode = (code: string) =>
  mobileClient.get<ApiResponse<SdkConfigByCode>>(`/v1/sdk/config/${encodeURIComponent(code)}`);

/** 라이브니스 (POST /v1/demo/liveness) */
export const demoLiveness = (apiKey: string, matchingFaceImage: Blob) => {
  const fd = new FormData();
  fd.append('apiKey', apiKey);
  fd.append('matchingFaceImage', matchingFaceImage, 'face.jpg');
  return mobileClient.post<ApiResponse<LivenessResponseDTO>>('/v1/demo/liveness', fd);
};

/* ─── SDK 토큰 기반 모바일 API ────────────────────────────
 * POST /v1/sdk/{module}/token — token 필드로 인증
 * ──────────────────────────────────────────────────────── */

/** SDK 등록 (POST /v1/sdk/user/token) */
export const sdkRegister = (code: string, faceImage: Blob, userDescription?: string) => {
  const fd = new FormData();
  fd.append('code', code);
  fd.append('faceImage', faceImage, 'face.jpg');
  if (userDescription) fd.append('userDescription', userDescription);
  return mobileClient.post<ApiResponse<UserResponseDTO>>('/v1/sdk/user/token', fd);
};

/** SDK 1:1 확인 (POST /v1/sdk/verify/token) */
export const sdkVerify = (code: string, faceId: string, matchingFaceImage: Blob, transactionUuid: string) => {
  const fd = new FormData();
  fd.append('code', code);
  fd.append('faceId', faceId);
  fd.append('matchingFaceImage', matchingFaceImage, 'face.jpg');
  fd.append('transactionUuid', transactionUuid);
  return mobileClient.post<ApiResponse<VerifyResponseDTO>>('/v1/sdk/verify/token', fd);
};

/** SDK 1:N 매칭 (POST /v1/sdk/identify/token) */
export const sdkIdentify = (code: string, matchingFaceImage: Blob, transactionUuid: string) => {
  const fd = new FormData();
  fd.append('code', code);
  fd.append('matchingFaceImage', matchingFaceImage, 'face.jpg');
  fd.append('transactionUuid', transactionUuid);
  return mobileClient.post<ApiResponse<IdentifyResponseDTO>>('/v1/sdk/identify/token', fd);
};

/** SDK 라이브니스 (POST /v1/sdk/liveness/token) */
export const sdkLiveness = (code: string, matchingFaceImage: Blob) => {
  const fd = new FormData();
  fd.append('code', code);
  fd.append('matchingFaceImage', matchingFaceImage, 'face.jpg');
  return mobileClient.post<ApiResponse<LivenessResponseDTO>>('/v1/sdk/liveness/token', fd);
};
