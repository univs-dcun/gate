interface AppConfig {
  apiBaseUrl?: string;
  qrUrlRewrite?: boolean;
  apiGuideUrl?: string;
  mobileBaseUrl?: string;
}

declare global {
  interface Window {
    __APP_CONFIG__?: AppConfig;
  }
}

export {};
