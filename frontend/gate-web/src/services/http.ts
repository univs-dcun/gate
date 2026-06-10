import axios from 'axios';

declare module 'axios' {
  interface AxiosRequestConfig {
    skipNetworkErrorRedirect?: boolean;
    _retry?: boolean;
  }
}

// 현재 선택된 프로젝트 API 키 (ProjectContext에서 갱신)
let currentApiKey: string | null = null;

export function setCurrentApiKey(key: string | null) {
  currentApiKey = key;
}

const BASE_URL =
  window.__APP_CONFIG__?.apiBaseUrl ||
  import.meta.env.VITE_API_BASE_URL ||
  '/api';

const httpClient = axios.create({
  baseURL: BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 인증 헤더(Authorization, X-Api-Key) 불필요 경로 (세션 없이 접근하는 auth 엔드포인트만)
const PUBLIC_PATH_PREFIXES = [
  '/v1/auth/signup',
  '/v1/auth/login',
  '/v1/auth/password/reset',
  '/v1/auth/token/refresh',
  '/v1/login',
];
const isPublicPath = (url?: string) =>
  !!url && PUBLIC_PATH_PREFIXES.some(prefix => url.startsWith(prefix));

/* ── JWT 유틸 ── */

/** JWT payload의 exp 클레임 추출 (Unix seconds), 파싱 실패 시 null */
function getTokenExp(token: string): number | null {
  try {
    // Base64URL → Base64 변환 후 디코딩
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const payload = JSON.parse(atob(base64)) as Record<string, unknown>;
    return typeof payload.exp === 'number' ? payload.exp : null;
  } catch {
    return null;
  }
}

/** 토큰이 thresholdSec초 이내에 만료되는지 확인 */
function isTokenExpiringSoon(token: string, thresholdSec = 60): boolean {
  const exp = getTokenExp(token);
  if (exp === null) return false;
  return exp - Date.now() / 1000 < thresholdSec;
}

/* ── Refresh Token 큐 ── */
let isRefreshing = false;
let failedQueue: Array<{ resolve: (token: string) => void; reject: (err: unknown) => void }> = [];

function processQueue(error: unknown, token: string | null = null) {
  failedQueue.forEach((p) => {
    if (error) p.reject(error);
    else       p.resolve(token!);
  });
  failedQueue = [];
}

function clearSession() {
  localStorage.removeItem('access_token');
  localStorage.removeItem('refresh_token');
}

function redirectToLogin() {
  clearSession();
  window.location.href = '/login';
}

/**
 * 토큰 갱신 공통 함수 (Proactive / Reactive 공용)
 * - 이미 갱신 중이면 큐에서 대기 후 새 토큰 반환
 * - 갱신 실패 시 세션 삭제 후 /login 리다이렉트
 */
async function doRefresh(): Promise<string> {
  const storedRefreshToken = localStorage.getItem('refresh_token');
  if (!storedRefreshToken) {
    redirectToLogin();
    throw new Error('No refresh token');
  }

  // 이미 갱신 중인 경우 큐에서 결과 대기
  if (isRefreshing) {
    return new Promise<string>((resolve, reject) => {
      failedQueue.push({ resolve, reject });
    });
  }

  isRefreshing = true;
  try {
    const res = await axios.post(
      `${BASE_URL}/v1/auth/token/refresh`,
      { refreshToken: storedRefreshToken },
      { headers: { 'Content-Type': 'application/json' } },
    );
    const { accessToken, refreshToken: newRefreshToken } = res.data.data as {
      accessToken: string;
      refreshToken?: string;
    };

    localStorage.setItem('access_token', accessToken);
    if (newRefreshToken) localStorage.setItem('refresh_token', newRefreshToken);

    processQueue(null, accessToken);
    return accessToken;
  } catch (err) {
    processQueue(err, null);
    redirectToLogin();
    throw err;
  } finally {
    isRefreshing = false;
  }
}

/* ── 요청 인터셉터 (Proactive Token Refresh 포함) ── */
httpClient.interceptors.request.use(
  async (config) => {
    config.headers['Accept-Language'] = localStorage.getItem('lang') ?? 'ko';
    config.headers['Accept-TimeZone'] = Intl.DateTimeFormat().resolvedOptions().timeZone;

    // public path는 인증 헤더 불필요
    if (isPublicPath(config.url)) return config;

    let token = localStorage.getItem('access_token');

    // 토큰 만료 60초 이내 → 선제 갱신
    if (token && isTokenExpiringSoon(token, 60)) {
      try {
        token = await doRefresh();
      } catch {
        return Promise.reject(new Error('Token refresh failed'));
      }
    }

    if (token) config.headers.Authorization = `Bearer ${token}`;
    if (currentApiKey) config.headers['X-Api-Key'] = currentApiKey;
    return config;
  },
  (error) => Promise.reject(error),
);

/* ── 응답 인터셉터 (Reactive Fallback) ── */
httpClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    // 네트워크 오류 (서버 응답 없음)
    if (
      !error.response &&
      !['/network-error', '/m/network-error'].includes(window.location.pathname) &&
      !error.config?.skipNetworkErrorRedirect
    ) {
      const isMobile = window.location.pathname.startsWith('/m/');
      window.location.href = isMobile ? '/m/network-error' : '/network-error';
      return Promise.reject(error);
    }

    const status          = error.response?.status;
    const originalRequest = error.config;

    // 401 처리 — proactive refresh가 실패하거나 race condition 발생 시 fallback
    if (status === 401 && window.location.pathname !== '/login') {
      // 이미 재시도했다면 바로 로그아웃
      if (originalRequest?._retry) {
        redirectToLogin();
        return Promise.reject(error);
      }

      // 다른 요청이 이미 토큰을 갱신했는지 확인 (요청 시점 토큰 vs 현재 저장 토큰)
      const latestToken  = localStorage.getItem('access_token');
      const requestToken = (originalRequest?.headers?.Authorization as string | undefined)
        ?.split(' ')[1];
      if (latestToken && requestToken && latestToken !== requestToken) {
        originalRequest._retry = true;
        originalRequest.headers.Authorization = `Bearer ${latestToken}`;
        return httpClient(originalRequest);
      }

      if (!localStorage.getItem('refresh_token')) {
        redirectToLogin();
        return Promise.reject(error);
      }

      originalRequest._retry = true;
      try {
        const newToken = await doRefresh();
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return httpClient(originalRequest);
      } catch (refreshError) {
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  },
);

/* ── 세션 만료 감시 (UG-121) ── */

/** 인증이 필요 없는 페이지 경로 */
const PUBLIC_PAGES = ['/login', '/signup', '/verify-email', '/set-password', '/network-error', '/m/'];
const isOnPublicPage = () => PUBLIC_PAGES.some(p => window.location.pathname.startsWith(p));

/**
 * 세션 유효성 확인 및 자동 갱신/로그인 전환
 * - access token 만료 임박 시 refresh 시도
 * - refresh token도 만료된 경우 /login 자동 이동
 */
async function checkSession(): Promise<void> {
  if (isOnPublicPage()) return;

  const token = localStorage.getItem('access_token');
  if (!token) return; // 비로그인 상태

  if (isTokenExpiringSoon(token, 60)) {
    try {
      await doRefresh();
    } catch {
      // doRefresh() 내부에서 redirectToLogin() 처리됨
    }
  }
}

// 60초마다 세션 유효성 체크 (아이들 상태에서 만료 감지)
setInterval(checkSession, 60_000);

// 탭이 백그라운드에서 포그라운드로 복귀할 때 즉시 체크
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible') checkSession();
});

export default httpClient;
