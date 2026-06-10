import httpClient from './http';

export interface UsageSummary {
  periodCount: number;  // 선택 기간 내 건수
  totalCount:  number;  // 전체 누적 건수
}

export interface DashboardSummary {
  registration:  UsageSummary;
  verifyById:    UsageSummary;   // 1:1 촬영인증
  verifyByImage: UsageSummary;   // 1:1 사진인증
  identify:      UsageSummary;
  liveness:      UsageSummary;
}

export type TrendPeriod = 'WEEK' | 'MONTH' | 'YEAR';

/** 인증 방식 — 대시보드 데이터 모달리티 필터 */
export type DashFeatureType = 'FACE' | 'PALM';

export interface DashboardTrend {
  period:        string;
  labels:        string[];
  registration:  number[];
  verifyById:    number[];   // 1:1 촬영인증
  verifyByImage: number[];   // 1:1 사진인증
  identify:      number[];
  liveness:      number[];
}

export interface DailyRow {
  date:          string;
  registration:  number;
  verifyById:    number;   // 1:1 촬영인증
  verifyByImage: number;   // 1:1 사진인증
  identify:      number;
  liveness:      number;
}

export interface DashboardDaily {
  contents: DailyRow[];
  page: {
    pageSize:      number;
    page:          number;
    totalElements: number;
    totalPages:    number;
  };
}

export interface RatioSummary {
  primaryPercent:   number;
  secondaryPercent: number;
  primaryCount:     number;
  secondaryCount:   number;
}

export interface DashboardRatios {
  registration:  RatioSummary;
  verifyById:    RatioSummary;   // 1:1 촬영인증
  verifyByImage: RatioSummary;   // 1:1 사진인증
  identify:      RatioSummary;
  liveness:      RatioSummary;
}

interface ApiResponse<T> {
  success: boolean;
  data:    T;
  errors:  { code: string; type: string; message: string } | null;
}

export const getDashboardSummary = (period?: TrendPeriod, featureType?: DashFeatureType) =>
  httpClient.get<ApiResponse<DashboardSummary>>('/v1/dashboard/summary', {
    params: { ...(period ? { period } : {}), ...(featureType ? { featureType } : {}) },
  });

export const getDashboardTrend = (period: TrendPeriod, featureType?: DashFeatureType) =>
  httpClient.get<ApiResponse<DashboardTrend>>('/v1/dashboard/trend', {
    params: { period, ...(featureType ? { featureType } : {}) },
  });

export const getDashboardDaily = (page: number, pageSize: number, featureType?: DashFeatureType) =>
  httpClient.get<ApiResponse<DashboardDaily>>('/v1/dashboard/daily', {
    params: { page, pageSize, ...(featureType ? { featureType } : {}) },
  });

export const getDashboardRatios = (period?: TrendPeriod, featureType?: DashFeatureType) =>
  httpClient.get<ApiResponse<DashboardRatios>>('/v1/dashboard/ratios', {
    params: { ...(period ? { period } : {}), ...(featureType ? { featureType } : {}) },
  });
