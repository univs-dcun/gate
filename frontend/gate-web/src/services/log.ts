import httpClient from './http';
import type { LogModule, LogResult, LogEntry } from '@/components/common';

/* ── 이미지 경로가 추가된 확장 LogEntry (consent ON 전용) ── */
export interface LogEntryWithImages extends LogEntry {
  username?:              string;
  faceImagePath?:         string;  // 등록된 얼굴 이미지 URL
  matchingFaceImagePath?: string;  // 매칭에 사용된 얼굴 이미지 URL
}

export type MatchingType = 'VERIFY' | 'VERIFY_ID' | 'VERIFY_IMAGE' | 'IDENTIFY' | 'LIVENESS' | 'REGISTER';

export interface MatchLog {
  transactionUuid:        string;
  matchingHistoryId:      number | null; // 일련번호
  matchType:              MatchingType;  // 기능(모듈)
  featureType?:           'FACE' | 'PALM'; // 인증 방식
  success?:               boolean | null;
  similarity?:            number | null;
  checkLiveness?:         boolean | null;
  failureType?:           string | null;
  failureReason?:         string | null;
  // ※ 백엔드 응답 용어 변경(특징점 관리와 동일): FID → featureId(문자열), 특징점 일련번호 → featureSeq(정수)
  featureId:                string | null;  // FID
  featureSeq?:              number | null;  // 특징점 일련번호(DB 식별자)
  username:                 string | null;  // 사용자명
  description:              string | null;  // 메모
  featureImagePath:         string | null;  // 등록된 특징 이미지 URL
  matchingFeatureImagePath: string | null;  // 매칭에 사용된 특징 이미지 URL
  matchingTime:           string;
  updatedAt?:             string;
  consentSnapshot?:       boolean;
}

interface LogsData {
  contents: MatchLog[];
  page: {
    totalElements: number;
    totalPages:    number;
    page:          number;
    pageSize:      number;
    totalCount?:   number;
  };
}

interface ApiResponse<T> {
  success: boolean;
  data:    T;
  errors:  { code: string; type: string; message: string } | null;
}

export type MatchingResultType = 'ALL' | 'SUCCESS' | 'FAILURE';

export interface GetLogsParams {
  matchType?:        string;
  matchResultType?:  MatchingResultType;
  matchingKeyword?:  string;
  featureType?:      'FACE' | 'PALM';   // 인증 방식 필터 (백엔드 지원 시 서버 필터)
  startDate?:        string;
  endDate?:          string;
  page?:             number;
  pageSize?:         number;
}

export const getLogs = (params: GetLogsParams) =>
  httpClient.get<ApiResponse<LogsData>>('/v1/match', { params });

/* ── 변환 유틸 ──────────────────────────────────────────── */

const MATCHING_TYPE_LABEL: Record<string, LogModule> = {
  VERIFY:       '1:1 촬영인증',
  VERIFY_ID:    '1:1 촬영인증',
  VERIFY_IMAGE: '1:1 사진인증',
  IDENTIFY:     '1:N 매칭',
  LIVENESS:     '라이브니스',
  REGISTER:     '등록',
};

function getLogResult(item: MatchLog): LogResult {
  if (item.matchType === 'LIVENESS') {
    return item.success ? '리얼' : '페이크';
  }
  return item.success ? '성공' : '실패';
}

export function toLogEntry(item: MatchLog): LogEntry {
  return {
    id:            String(item.matchingHistoryId ?? item.transactionUuid),
    serialNo:      item.matchingHistoryId ?? undefined,
    module:        MATCHING_TYPE_LABEL[item.matchType] ?? '1:1 촬영인증',
    authMethod:    item.featureType ?? 'FACE',
    requestId:     item.transactionUuid,
    result:        getLogResult(item),
    fid:           item.featureId ?? undefined,
    featureSeq:    item.featureSeq ?? undefined,
    score:         item.similarity ?? undefined,
    checkLiveness: item.checkLiveness ?? undefined,
    memo:          item.description ?? undefined,
    failureType:   item.failureType ?? undefined,
    failureReason:    item.failureReason ?? undefined,
    createdAt:        item.matchingTime,
    consentSnapshot:  item.consentSnapshot ?? false,
  };
}

export function toLogEntryWithImages(item: MatchLog): LogEntryWithImages {
  return {
    ...toLogEntry(item),
    username:              item.username ?? undefined,
    faceImagePath:         item.featureImagePath ?? undefined,
    matchingFaceImagePath: item.matchingFeatureImagePath ?? undefined,
  };
}

export function getDateRangeParams(range: string): { startDate: string; endDate: string } {
  const end   = new Date();
  const start = new Date();
  if (range === '최근 30일')       start.setDate(end.getDate() - 30);
  else if (range === '최근 3개월') start.setMonth(end.getMonth() - 3);
  else                             start.setDate(end.getDate() - 7);
  return {
    startDate: start.toISOString().split('T')[0],
    endDate:   end.toISOString().split('T')[0],
  };
}
