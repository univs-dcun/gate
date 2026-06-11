import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Pagination from './Pagination';
import { ModuleTypeIcon, AuthMethodBadge } from '@/components/ui/icons';

export type LogResult = '성공' | '실패' | '리얼' | '페이크';
export type LogModule = '등록' | '1:1 촬영인증' | '1:1 사진인증' | '1:N 매칭' | '라이브니스';

export interface LogEntry {
  id:              string;
  serialNo?:       number;
  module:          LogModule;
  authMethod?:     'FACE' | 'PALM';   // 인증 방식 (얼굴/손바닥) — 백엔드 제공 시 표시
  requestId:       string;
  result:          LogResult;
  fid?:            string;        // Feature ID (구 FID)
  featureSeq?:     number;        // 특징점 일련번호
  score?:          number;
  checkLiveness?:  boolean;
  memo?:           string;
  failureType?:      string;
  failureReason?:    string;
  createdAt:         string;
  consentSnapshot?:  boolean;
}

interface LogTableProps {
  data:              LogEntry[];
  totalCount?:       number;
  totalRecords?:     number;
  page?:             number;
  pageSize?:         number;
  onPageChange?:     (page: number) => void;
  onPageSizeChange?: (size: number) => void;
  isLoading?:        boolean;
}

/* ── 모듈별 색상 및 아이콘 타입 ── */
const MODULE_CONFIG: Record<LogModule, { color: string; iconType: string; i18nKey: string }> = {
  '등록':        { color: '#4bbbfb', iconType: 'enroll',   i18nKey: 'module.enrollment' },
  '1:1 촬영인증': { color: '#f59e0b', iconType: 'verify',   i18nKey: 'module.verification' },
  '1:1 사진인증': { color: '#02aaa4', iconType: 'verify',   i18nKey: 'module.verify_image' },
  '1:N 매칭':    { color: '#8b5cf6', iconType: 'match',    i18nKey: 'module.matching' },
  '라이브니스':   { color: '#10b981', iconType: 'liveness', i18nKey: 'module.liveness' },
};

/* ── 결과별 배지 색상 (성공=그린, 실패=레드, 둘 다 테두리) ── */
const RESULT_CONFIG: Record<LogResult, { bg: string; text: string; border: string; i18nKey: string }> = {
  '성공':  { bg: '#f4fcfb', text: '#0b8b61', border: '#0fb981', i18nKey: 'logs.success' },
  '리얼':  { bg: '#f4fcfb', text: '#0b8b61', border: '#0fb981', i18nKey: 'logs.real' },
  '실패':  { bg: '#fff7f6', text: '#d83232', border: '#f3b4b4', i18nKey: 'logs.failure' },
  '페이크': { bg: '#fff7f6', text: '#d83232', border: '#f3b4b4', i18nKey: 'logs.fake' },
};

/* ── 모듈 아이콘 ── */

/* ── 정렬 아이콘 ── */
const SortArrows = () => (
  <span className="inline-flex flex-col gap-[2px] ml-1 align-middle">
    <svg width="10" height="5" viewBox="0 0 10 5" fill="none">
      <path d="M5 0L0.669872 4.5H9.33013L5 0Z" fill="#94a3b8" />
    </svg>
    <svg width="10" height="5" viewBox="0 0 10 5" fill="none">
      <path d="M5 5L9.33013 0.5H0.669872L5 5Z" fill="#94a3b8" />
    </svg>
  </span>
);

/* ── 헤더 스타일 상수 ── */
const TH = 'px-2.5 py-3 text-center text-[14px] font-semibold text-[#475569] tracking-[-0.4px] whitespace-nowrap sticky-th';
const TD_BASE = 'px-2.5 border-r border-[#e2e8f0] last:border-r-0';

function LogTable({ data, totalCount, totalRecords, page: pageProp, pageSize: pageSizeProp, onPageChange, onPageSizeChange, isLoading }: LogTableProps) {
  const { t } = useTranslation();
  const [localPage, setLocalPage] = useState(1);
  const [localPageSize, setLocalPageSize] = useState(10);

  /* controlled(서버 페이지네이션) vs uncontrolled(클라이언트) */
  const isControlled = pageProp !== undefined && onPageChange !== undefined;
  const page     = isControlled ? pageProp : localPage;
  const setPage  = isControlled ? onPageChange : setLocalPage;
  const pageSize = pageSizeProp ?? localPageSize;
  const setPageSize = onPageSizeChange ?? ((s: number) => { setLocalPageSize(s); setLocalPage(1); });

  const total = totalCount ?? data.length;
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  /* controlled 모드: data는 이미 현재 페이지 데이터, uncontrolled: 직접 슬라이스 */
  const paged = isControlled ? data : data.slice((page - 1) * pageSize, page * pageSize);

  return (
    <div className="w-full bg-white">
      <div className="overflow-auto max-h-[calc(100vh-340px)]">
        <table className="w-full min-w-[900px]">
          <thead>
            <tr>
              <th className={TH}>{t('logs.serial_no')}</th>
              <th className={TH}>{t('projects.col_auth_method')}</th>
              <th className={TH}>{t('logs.test_module')}</th>
              <th className={`${TH} !px-2`} style={{ width: '175px' }}>{t('logs.request_id')}</th>
              <th className={`${TH} !px-2`} style={{ width: '175px' }}>Feature ID</th>
              <th className={[TH, 'min-w-[160px]'].join(' ')}>{t('logs.memo')}</th>
              <th className={TH}>{t('logs.result')}</th>
              <th className={TH}>{t('logs.score')}</th>
              <th className={TH}>{t('logs.check_liveness')}</th>
              <th className={TH}>
                <span className="inline-flex items-center gap-1">
                  {t('logs.created_at')}
                  <SortArrows />
                </span>
              </th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr>
                <td colSpan={10} className="px-2.5 py-10 text-center text-sm text-[var(--color-text-disabled)]">
                  {t('common.loading')}
                </td>
              </tr>
            ) : paged.length === 0 ? (
              <tr>
                <td colSpan={10} className="px-2.5 py-10 text-center text-sm text-[var(--color-text-disabled)]">
                  {t('common.no_data')}
                </td>
              </tr>
            ) : (
              paged.map((row) => {
                const mod = MODULE_CONFIG[row.module];
                const res = RESULT_CONFIG[row.result];
                const isPalm = row.authMethod === 'PALM'; // 기본 얼굴인증
                return (
                  <tr
                    key={row.id}
                    className="border-b border-[#e2e8f0] hover:bg-[#f8fafc] transition-colors"
                  >
                    {/* 일련번호 */}
                    <td className={`${TD_BASE} py-[18px] text-center text-[15px] font-medium text-[#334155] tracking-[-0.375px]`}>
                      {row.serialNo ?? '-'}
                    </td>
                    {/* 인증 방식 (기본 얼굴) */}
                    <td className={`${TD_BASE} py-[18px] text-center`}>
                      <AuthMethodBadge palm={isPalm} label={isPalm ? t('auth_type.palm_short') : t('auth_type.face_short')} />
                    </td>
                    {/* 테스트 모듈 */}
                    <td className={`${TD_BASE} py-[18px]`}>
                      <div className="flex items-center gap-1">
                        <ModuleTypeIcon module={row.module} size={22} />
                        <span className="text-[15px] font-medium tracking-[-0.375px] whitespace-nowrap" style={{ color: mod.color }}>
                          {t(mod.i18nKey)}
                        </span>
                      </div>
                    </td>
                    {/* 요청 ID */}
                    <td className={`${TD_BASE} py-[18px]`}>
                      <span className="block break-all text-center text-[13px] font-medium text-[#334155] tracking-[-0.3px] font-mono">
                        {row.requestId}
                      </span>
                    </td>
                    {/* FID */}
                    <td className={`${TD_BASE} py-[18px]`}>
                      <span className="block break-all text-center text-[13px] font-medium text-[#334155] tracking-[-0.3px] font-mono">
                        {row.fid ?? '-'}
                      </span>
                    </td>
                    {/* 메모 — 길면 줄바꿈(최대 폭 제한) */}
                    <td className={`${TD_BASE} py-[18px]`}>
                      <span className="block max-w-[220px] break-words whitespace-normal text-[15px] font-medium text-[#334155] tracking-[-0.375px]">
                        {row.memo || '-'}
                      </span>
                    </td>
                    {/* 결과 */}
                    <td className={`${TD_BASE} py-[13px] text-center`}>
                      {(row.result === '실패' || row.result === '페이크') ? (
                        <div className="relative inline-flex group">
                          <span
                            className="inline-flex items-center justify-center px-4 py-1.5 rounded-[8px] border text-[14px] font-medium tracking-[-0.35px] whitespace-nowrap cursor-default"
                            style={{ backgroundColor: res.bg, color: res.text, borderColor: res.border }}
                          >
                            {t(res.i18nKey)}
                          </span>
                          {(row.failureType || row.failureReason) && (
                            <div className={[
                              'absolute bottom-[calc(100%+6px)] left-1/2 -translate-x-1/2 z-50',
                              'bg-[#1e293b] text-white text-[12px] font-medium tracking-[-0.3px]',
                              'rounded-[6px] px-3 py-2 pointer-events-none whitespace-nowrap',
                              'opacity-0 group-hover:opacity-100 transition-opacity duration-150',
                            ].join(' ')}>
                              {row.failureType && (
                                <p className="text-[#94a3b8] text-[11px] tracking-[-0.275px] mb-0.5 whitespace-nowrap">{row.failureType}</p>
                              )}
                              <p className="leading-snug">
                                {row.failureReason ?? row.failureType}
                              </p>
                              <span className="absolute top-full left-1/2 -translate-x-1/2 border-4 border-transparent border-t-[#1e293b]" />
                            </div>
                          )}
                        </div>
                      ) : (
                        <span
                          className="inline-flex items-center justify-center px-4 py-1.5 rounded-[8px] border text-[14px] font-medium tracking-[-0.35px] whitespace-nowrap"
                          style={{ backgroundColor: res.bg, color: res.text, borderColor: res.border }}
                        >
                          {t(res.i18nKey)}
                        </span>
                      )}
                    </td>
                    {/* 스코어 */}
                    <td className={`${TD_BASE} py-[18px] text-right text-[15px] font-medium text-[#334155] tracking-[-0.375px]`}>
                      {row.module !== '라이브니스' && row.score !== undefined ? `${row.score}%` : '-'}
                    </td>
                    {/* 라이브니스 적용 여부 */}
                    <td className={`${TD_BASE} py-[18px] text-center`}>
                      {row.checkLiveness === true ? (
                        <span className="inline-flex items-center justify-center px-3 py-1 rounded-[6px] text-[13px] font-medium tracking-[-0.325px] whitespace-nowrap bg-[#ecfdf5] text-[#10b981]">
                          ON
                        </span>
                      ) : row.checkLiveness === false ? (
                        <span className="inline-flex items-center justify-center px-3 py-1 rounded-[6px] text-[13px] font-medium tracking-[-0.325px] whitespace-nowrap bg-[#f1f5f9] text-[#64748b]">
                          OFF
                        </span>
                      ) : (
                        <span className="text-[15px] font-medium text-[#334155] tracking-[-0.375px]">-</span>
                      )}
                    </td>
                    {/* 생성 일시 */}
                    <td className={`${TD_BASE} py-[18px] text-center text-[15px] font-medium text-[#334155] tracking-[-0.375px] whitespace-nowrap`}>
                      {row.createdAt}
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
      <Pagination
        pageNum={page}
        pageSize={pageSize}
        totalPages={totalPages}
        totalElements={total}
        totalRecords={totalRecords}
        currentCount={paged.length}
        onPageChange={setPage}
        onPageSizeChange={setPageSize}
      />
    </div>
  );
}

export default LogTable;
