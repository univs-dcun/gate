import httpClient from './http';

/* ── 타입 ──────────────────────────────────────────────── */
export interface SendEmailCodeResponse {
  email: string;
  expiresAt: string;
}

export interface VerifyEmailCodeResponse {
  verified: boolean;
}

export interface SignupResponse {
  accountId: number;
  email: string;
  createdAt: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  accountResponseDTO: {
    accountId: number;
    email: string;
    lastLoginAt: string;
  };
}

interface ApiResponse<T> {
  success: boolean;
  data: T;
  errors: Record<string, string> | null;
}

/* ── API 함수 ───────────────────────────────────────────── */

/** 이메일 인증번호 발송 */
export const sendEmailCode = (email: string) =>
  httpClient.post<ApiResponse<SendEmailCodeResponse>>(
    '/v1/auth/signup/send-code',
    { email },
  );

/** 이메일 인증번호 검증 */
export const verifyEmailCode = (email: string, code: string) =>
  httpClient.post<ApiResponse<VerifyEmailCodeResponse>>(
    '/v1/auth/signup/verify-code',
    { email, code },
  );

/** 회원가입 */
export const signup = (email: string, password: string, passwordConfirm: string) =>
  httpClient.post<ApiResponse<SignupResponse>>(
    '/v1/auth/signup',
    { email, password, passwordConfirm },
  );

/** 로그인 */
export const login = (email: string, password: string) =>
  httpClient.post<ApiResponse<LoginResponse>>(
    '/v1/auth/login',
    { email, password },
  );

/** 비밀번호 재설정 인증번호 발송 */
export const sendPasswordResetCode = (email: string) =>
  httpClient.post<ApiResponse<null>>(
    '/v1/auth/password/reset/send-code',
    { email },
  );

/** 비밀번호 재설정 인증번호 검증 */
export const verifyPasswordResetCode = (email: string, code: string) =>
  httpClient.post<ApiResponse<VerifyEmailCodeResponse>>(
    '/v1/auth/password/reset/verify-code',
    { email, code },
  );

/** 비밀번호 재설정 */
export const resetPassword = (email: string, newPassword: string, passwordConfirm: string) =>
  httpClient.post<ApiResponse<null>>(
    '/v1/auth/password/reset',
    { email, newPassword, passwordConfirm },
  );

export interface RefreshTokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

/** 비밀번호 변경 (세션 있을 때) */
export const changePassword = (accountId: number, password: string, newPassword: string) =>
  httpClient.put<ApiResponse<null>>(
    '/v1/auth/password/change',
    { accountId, password, newPassword },
  );

/** 토큰 갱신 */
export const refreshTokenApi = (refreshToken: string) =>
  httpClient.post<ApiResponse<RefreshTokenResponse>>(
    '/v1/auth/token/refresh',
    { refreshToken },
  );
