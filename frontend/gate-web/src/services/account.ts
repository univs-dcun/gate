import httpClient from './http';

/* ── 타입 ──────────────────────────────────────────────── */
export interface CompanyInfo {
  companyId:      number;
  accountId:      number;
  companyName:    string;
  businessNumber: string;
  managerMail:    string;
  managerName:    string;
  managerNumber:  string;
  mainService:    string;
  businessType:   string;
  employeeCount:  string;
}

interface ApiResponse<T> {
  success: boolean;
  data:    T;
  errors:  Record<string, string> | null;
}

/* ── API 함수 ───────────────────────────────────────────── */

export interface UpsertCompanyRequest {
  companyName:    string;
  businessNumber: string;
  managerName:    string;
  managerNumber:  string;
  mainService:    string;
  businessType:   string;
  employeeCount:  string;
}

/** 회사(계정) 정보 조회 */
export const getCompanyInfo = () =>
  httpClient.get<ApiResponse<CompanyInfo>>('/v1/company');

/** 회사(계정) 정보 등록/수정 */
export const upsertCompanyInfo = (body: UpsertCompanyRequest) =>
  httpClient.put<ApiResponse<CompanyInfo>>('/v1/company', body);
