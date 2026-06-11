/**
 * LogTableConsent — 개인정보 동의 ON 시 사용하는 로그 테이블
 * 기존 LogTable에 "매칭 이미지" 컬럼 추가
 */

import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import Pagination from './Pagination';
import { ModuleTypeIcon, AuthMethodBadge } from '@/components/ui/icons';
import type { LogModule, LogResult } from './LogTable';
import type { LogEntryWithImages } from '@/services/log';

/* ── 모듈/결과 설정 (LogTable과 동일) ── */
const MODULE_CONFIG: Record<LogModule, { color: string; iconType: string; i18nKey: string }> = {
  '등록':     { color: '#4bbbfb', iconType: 'enroll',   i18nKey: 'module.enrollment' },
  '1:1 촬영인증': { color: '#f59e0b', iconType: 'verify',   i18nKey: 'module.verification' },
  '1:1 사진인증': { color: '#02aaa4', iconType: 'verify',   i18nKey: 'module.verify_image' },
  '1:N 매칭': { color: '#8b5cf6', iconType: 'match',    i18nKey: 'module.matching' },
  '라이브니스': { color: '#10b981', iconType: 'liveness', i18nKey: 'module.liveness' },
};

const RESULT_CONFIG: Record<LogResult, { bg: string; text: string; border: string; i18nKey: string }> = {
  '성공':  { bg: '#f4fcfb', text: '#0b8b61', border: '#0fb981', i18nKey: 'logs.success'  },
  '실패':  { bg: '#fff7f6', text: '#d83232', border: '#f3b4b4', i18nKey: 'logs.failure'  },
  '리얼':  { bg: '#f4fcfb', text: '#0b8b61', border: '#0fb981', i18nKey: 'logs.real'     },
  '페이크': { bg: '#fff7f6', text: '#d83232', border: '#f3b4b4', i18nKey: 'logs.fake'    },
};

const TH = 'px-2.5 py-3 text-center text-[14px] font-semibold text-[#475569] tracking-[-0.4px] whitespace-nowrap sticky-th';
const TD_BASE = 'px-2.5 border-r border-[#e2e8f0] last:border-r-0';

/* ── 아이콘 ── */

const SortArrows = () => (
  <span className="inline-flex flex-col gap-[2px] ml-1 align-middle">
    <svg width="8" height="5" viewBox="0 0 8 5"><path d="M4 0L8 5H0L4 0Z" fill="#94A3B8" /></svg>
    <svg width="8" height="5" viewBox="0 0 8 5"><path d="M4 5L0 0H8L4 5Z" fill="#94A3B8" /></svg>
  </span>
);

/* ── 얼굴 이미지 썸네일 ── */
function FaceThumb({ src, highlight }: { src?: string; highlight?: boolean }) {
  const [err, setErr] = useState(false);
  const base = 'w-[36px] h-[36px] object-cover rounded-[6px] flex-shrink-0';
  const placeholder = (
    <div className={[
      'w-[36px] h-[36px] rounded-[6px] flex items-center justify-center flex-shrink-0',
      highlight ? 'bg-[#8a58ff]/20 ring-1 ring-[#8a58ff]' : 'bg-[#e2e8f0]',
    ].join(' ')}>
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.5" strokeLinecap="round">
        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
      </svg>
    </div>
  );
  if (!src || err) return placeholder;
  return (
    <img
      src={src}
      alt="face"
      onError={() => setErr(true)}
      className={[base, highlight ? 'ring-1 ring-[#8a58ff]' : ''].join(' ')}
    />
  );
}

/* ── 매칭 이미지 셀 — 모듈에 따라 1장 또는 2장 ── */
function MatchingImageCell({ entry }: { entry: LogEntryWithImages }) {
  const hasTwoImages = entry.module === '1:1 촬영인증' || entry.module === '1:1 사진인증' || entry.module === '1:N 매칭';

  /* 등록: matchingFaceImagePath 우선, 없으면 faceImagePath */
  const singleSrc = entry.module === '등록'
    ? (entry.matchingFaceImagePath || entry.faceImagePath)
    : entry.matchingFaceImagePath; // 라이브니스

  if (hasTwoImages) {
    return (
      <div className="flex items-center gap-1.5">
        {/* 기준 얼굴(등록) */}
        <FaceThumb src={entry.faceImagePath} />
        {/* 촬영 얼굴(매칭) — 보라 테두리 강조 */}
        <FaceThumb src={entry.matchingFaceImagePath} highlight />
      </div>
    );
  }

  return <FaceThumb src={singleSrc} />;
}

/* ── Props ── */
interface LogTableConsentProps {
  data:              LogEntryWithImages[];
  totalCount?:       number;
  totalRecords?:     number;
  page?:             number;
  pageSize?:         number;
  onPageChange?:     (page: number) => void;
  onPageSizeChange?: (size: number) => void;
  isLoading?:        boolean;
  onRowClick?:       (entry: LogEntryWithImages) => void;
}

export default function LogTableConsent({
  data,
  totalCount,
  totalRecords,
  page: pageProp,
  pageSize: pageSizeProp,
  onPageChange,
  onPageSizeChange,
  isLoading,
  onRowClick,
}: LogTableConsentProps) {
  const { t } = useTranslation();
  const [localPage, setLocalPage] = useState(1);
  const [localPageSize, setLocalPageSize] = useState(10);

  const isControlled = pageProp !== undefined && onPageChange !== undefined;
  const page        = isControlled ? pageProp     : localPage;
  const setPage     = isControlled ? onPageChange : setLocalPage;
  const pageSize    = pageSizeProp ?? localPageSize;
  const setPageSize = onPageSizeChange ?? ((s: number) => { setLocalPageSize(s); setLocalPage(1); });

  const total      = totalCount ?? data.length;
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const paged      = isControlled ? data : data.slice((page - 1) * pageSize, page * pageSize);

  return (
    <div className="w-full bg-white">
      <div className="overflow-auto max-h-[calc(100vh-340px)]">
        <table className="w-full min-w-[1188px] table-fixed">
          <thead>
            <tr>
              <th className={TH} style={{ width: '70px' }}>{t('logs.serial_no')}</th>
              <th className={TH} style={{ width: '90px' }}>{t('projects.col_auth_method')}</th>
              <th className={TH} style={{ width: '120px' }}>{t('logs.test_module')}</th>
              <th className={`${TH} !px-2`} style={{ width: '170px' }}>{t('logs.request_id')}</th>
              <th className={`${TH} !px-2`} style={{ width: '170px' }}>Feature ID</th>
              <th className={TH} style={{ width: '80px' }}>{t('logs.memo')}</th>
              <th className={TH} style={{ width: '100px' }}>{t('logs.result')}</th>
              {/* consent 추가 컬럼 */}
              <th className={TH} style={{ width: '108px' }}>{t('logs.matching_image')}</th>
              <th className={TH} style={{ width: '70px' }}>{t('logs.score')}</th>
              <th className={TH} style={{ width: '80px' }}>{t('logs.check_liveness')}</th>
              <th className={TH} style={{ width: '130px' }}>
                <span className="inline-flex items-center justify-center gap-1">
                  {t('logs.created_at')}
                  <SortArrows />
                </span>
              </th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr>
                <td colSpan={11} className="px-2.5 py-10 text-center text-sm text-[var(--color-text-disabled)]">
                  {t('common.loading')}
                </td>
              </tr>
            ) : paged.length === 0 ? (
              <tr>
                <td colSpan={11} className="px-2.5 py-10 text-center text-sm text-[var(--color-text-disabled)]">
                  {t('common.no_data')}
                </td>
              </tr>
            ) : (
              paged.map((row) => {
                const mod = MODULE_CONFIG[row.module];
                const res = RESULT_CONFIG[row.result];
                return (
                  <tr
                    key={row.id}
                    className={['border-b border-[#e2e8f0] hover:bg-[#f8fafc] transition-colors', onRowClick ? 'cursor-pointer' : ''].join(' ')}
                    onClick={() => onRowClick?.(row)}
                  >
                    {/* 일련번호 */}
                    <td className={`${TD_BASE} py-2 text-center text-[15px] font-medium text-[#334155] tracking-[-0.375px]`}>
                      {row.serialNo ?? '-'}
                    </td>
                    {/* 인증 방식 (기본 얼굴) */}
                    <td className={`${TD_BASE} py-2 text-center`}>
                      <AuthMethodBadge palm={row.authMethod === 'PALM'} label={row.authMethod === 'PALM' ? t('auth_type.palm_short') : t('auth_type.face_short')} />
                    </td>
                    {/* 테스트 모듈 */}
                    <td className={`${TD_BASE} py-2`}>
                      <div className="flex items-center gap-1">
                        <ModuleTypeIcon module={row.module} size={22} />
                        <span className="text-[15px] font-medium tracking-[-0.375px] whitespace-nowrap" style={{ color: mod.color }}>
                          {t(mod.i18nKey)}
                        </span>
                      </div>
                    </td>
                    {/* 요청 ID */}
                    <td className="border-r border-[#e2e8f0] py-2 px-2">
                      <span className="block break-all text-center text-[13px] font-medium text-[#334155] tracking-[-0.3px] font-mono">
                        {row.requestId}
                      </span>
                    </td>
                    {/* FID */}
                    <td className="border-r border-[#e2e8f0] py-2 px-2">
                      <span className="block break-all text-center text-[13px] font-medium text-[#334155] tracking-[-0.3px] font-mono">
                        {row.fid ?? '-'}
                      </span>
                    </td>
                    {/* 메모 — 고정폭 컬럼 내 줄바꿈 */}
                    <td className={`${TD_BASE} py-2 text-[15px] font-medium text-[#334155] tracking-[-0.375px]`}>
                      <span className="block break-words whitespace-normal">{row.memo || '-'}</span>
                    </td>
                    {/* 결과 */}
                    <td className={`${TD_BASE} py-2 text-center`}>
                      {(row.result === '실패' || row.result === '페이크') ? (
                        <div className="relative inline-flex group">
                          <span
                            className="inline-flex items-center justify-center px-4 py-1.5 rounded-[8px] border text-[14px] font-medium tracking-[-0.35px] whitespace-nowrap cursor-default"
                            style={{ backgroundColor: res.bg, color: res.text, borderColor: res.border }}
                          >
                            {t(res.i18nKey)}
                          </span>
                          {(row.failureType || row.failureReason) && (
                            <div className={['absolute bottom-[calc(100%+6px)] left-1/2 -translate-x-1/2 z-50', 'bg-[#1e293b] text-white text-[12px] font-medium tracking-[-0.3px]', 'rounded-[6px] px-3 py-2 pointer-events-none whitespace-nowrap', 'opacity-0 group-hover:opacity-100 transition-opacity duration-150'].join(' ')}>
                              {row.failureType && <p className="text-[#94a3b8] text-[11px] tracking-[-0.275px] mb-0.5">{row.failureType}</p>}
                              <p className="leading-snug">{row.failureReason ?? row.failureType}</p>
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
                    {/* 매칭 이미지 — consentSnapshot true일 때만 표시 */}
                    <td className={`${TD_BASE} py-2`}>
                      {row.consentSnapshot ? (
                        <div className="flex justify-center">
                          <MatchingImageCell entry={row} />
                        </div>
                      ) : (
                        <div className="flex flex-col items-center gap-1 text-[#94a3b8]">
                          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                            <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                            <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                          </svg>
                          <span className="text-[11px] tracking-[-0.275px] whitespace-nowrap">{t('common.private')}</span>
                        </div>
                      )}
                    </td>
                    {/* 스코어 */}
                    <td className={`${TD_BASE} py-2 text-right text-[15px] font-medium text-[#334155] tracking-[-0.375px]`}>
                      {row.module !== '라이브니스' && row.score !== undefined ? `${row.score}%` : '-'}
                    </td>
                    {/* 라이브니스 */}
                    <td className={`${TD_BASE} py-2 text-center`}>
                      {row.checkLiveness === true ? (
                        <span className="inline-flex items-center justify-center px-3 py-1 rounded-[6px] text-[13px] font-medium tracking-[-0.325px] whitespace-nowrap bg-[#ecfdf5] text-[#10b981]">ON</span>
                      ) : row.checkLiveness === false ? (
                        <span className="inline-flex items-center justify-center px-3 py-1 rounded-[6px] text-[13px] font-medium tracking-[-0.325px] whitespace-nowrap bg-[#f1f5f9] text-[#64748b]">OFF</span>
                      ) : (
                        <span className="text-[15px] font-medium text-[#334155] tracking-[-0.375px]">-</span>
                      )}
                    </td>
                    {/* 생성 일시 */}
                    <td className={`${TD_BASE} py-2 text-center text-[15px] font-medium text-[#334155] tracking-[-0.375px]`}>
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
