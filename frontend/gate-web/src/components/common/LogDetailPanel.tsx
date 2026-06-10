/**
 * LogDetailPanel — 로그 상세 사이드 패널 (개인정보 동의 ON)
 *
 * 모듈 타입 × 성공/실패에 따라 자동으로 적합한 UI를 렌더링
 * - 등록 성공/실패
 * - 라이브니스 성공/실패
 * - 1:1 확인 성공/실패 (이미지 포함)
 * - 1:N 매칭 성공/실패 (이미지 포함)
 */

import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { LogModule, LogResult } from './LogTable';
import type { LogEntryWithImages } from '@/services/log';

/* ── 모듈 → 매칭 방식 문자열 역매핑 ── */
const MODULE_TO_MATCH_TYPE: Record<LogModule, string> = {
  '등록':        'REGISTER',
  '1:1 촬영인증': 'VERIFY_ID',
  '1:1 사진인증': 'VERIFY_IMAGE',
  '1:N 매칭':    'IDENTIFY',
  '라이브니스':   'LIVENESS',
};

/* 액션 라벨 → i18n 키 (t()로 출력) */
const MODULE_TO_ACTION: Record<LogModule, string> = {
  '등록':        'module.enrollment',
  '1:1 촬영인증': 'module.verification',
  '1:1 사진인증': 'module.verify_image',
  '1:N 매칭':    'module.matching',
  '라이브니스':   'module.liveness',
};

/* ── 결과 → 성공 여부 ── */
function isSuccess(result: LogResult) {
  return result === '성공' || result === '리얼';
}

/* ── 아이콘 (SVG inline) ── */
const CheckIcon = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="20 6 9 17 4 12" />
  </svg>
);
const ErrorIcon = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10" />
    <line x1="12" y1="8" x2="12" y2="12" />
    <line x1="12" y1="16" x2="12.01" y2="16" strokeWidth="3" />
  </svg>
);
const BarChartIcon = () => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="12" width="4" height="8" rx="1" /><rect x="10" y="7" width="4" height="13" rx="1" /><rect x="17" y="2" width="4" height="18" rx="1" />
  </svg>
);
const HistoryIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" />
  </svg>
);
const GalleryIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="3" width="18" height="18" rx="2" /><circle cx="8.5" cy="8.5" r="1.5" />
    <polyline points="21 15 16 10 5 21" />
  </svg>
);
const CloseIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
  </svg>
);

/* ── 작은 라벨 아이콘 ── */
const LabelItem = ({ icon, label, value, valueColor }: { icon: React.ReactNode; label: string; value: React.ReactNode; valueColor?: string }) => (
  <div className="flex flex-col gap-2 flex-1 min-w-0">
    <div className="flex items-center gap-1.5">
      <span className="text-[#64748b] flex-shrink-0">{icon}</span>
      <span className="text-[14px] text-[#64748b] tracking-[-0.35px] leading-5 whitespace-nowrap">{label}</span>
    </div>
    <span className={['text-[14px] font-semibold tracking-[-0.35px] leading-5', valueColor ?? 'text-[#334155]'].join(' ')}>
      {value}
    </span>
  </div>
);

const SmActionIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M9 12l2 2 4-4m6 2a9 9 0 1 1-18 0 9 9 0 0 1 18 0z" />
  </svg>
);
const SmUserIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
  </svg>
);
const SmAuthIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
  </svg>
);
const SmFileIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" /><line x1="16" y1="13" x2="8" y2="13" /><line x1="16" y1="17" x2="8" y2="17" />
  </svg>
);
const SmActivityIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
  </svg>
);
const SmClockIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" />
  </svg>
);
const SmCheckCircleIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="#0fb981" stroke="none">
    <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" stroke="#0fb981" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round" />
    <polyline points="22 4 12 14.01 9 11.01" stroke="#0fb981" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);
const SmXCircleIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#f59e0b" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10" /><line x1="15" y1="9" x2="9" y2="15" /><line x1="9" y1="9" x2="15" y2="15" />
  </svg>
);
const SmHelpIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10" /><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" /><line x1="12" y1="17" x2="12.01" y2="17" />
  </svg>
);

/* ── 이미지 셀 ── */
function FaceImage({ src, width, height, label, highlight }: { src?: string; width: number; height: number; label: string; highlight?: boolean }) {
  const [err, setErr] = useState(false);
  return (
    <div className={['inline-flex flex-col items-center gap-2', highlight ? 'rounded-[20px] overflow-hidden bg-[#8a58ff]' : ''].join(' ')}>
      <div style={{ width, height }} className={['relative overflow-hidden rounded-[8px] bg-[#e2e8f0] flex-shrink-0', highlight ? 'border border-[#8a58ff]' : ''].join(' ')}>
        {!err && src ? (
          <img src={src} alt="face" onError={() => setErr(true)} className="w-full h-full object-cover" />
        ) : (
          <div className="w-full h-full flex items-center justify-center">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.5" strokeLinecap="round">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
            </svg>
          </div>
        )}
      </div>
      <span className={['text-[14px] font-semibold tracking-[-0.35px] leading-5 whitespace-nowrap py-2', highlight ? 'text-white px-2' : 'text-[#64748b]'].join(' ')}>
        {label}
      </span>
    </div>
  );
}

/* ── ID 뱃지 박스 ── */
function IdBox({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-2 flex-1 min-w-0">
      <span className="text-[13px] font-semibold text-[#64748b] tracking-[-0.325px] leading-[1.4]">{label}</span>
      <div className="bg-white border border-[#f1f5f9] rounded-[8px] px-3 py-2">
        <span className="text-[12px] font-semibold text-[#64748b] tracking-[-0.3px] leading-5 break-all font-mono">{value || '-'}</span>
      </div>
    </div>
  );
}

/* ── Props ── */
export interface LogDetailPanelProps {
  entry: LogEntryWithImages;
  onClose: () => void;
}

/* ── 모듈별 실패 라벨 i18n 키 ── */
const FAILURE_LABEL_KEY: Record<LogModule, string> = {
  '등록':        'logs.failure_enroll',
  '1:1 촬영인증': 'logs.failure_auth',
  '1:1 사진인증': 'logs.failure_auth',
  '1:N 매칭':    'logs.failure_auth',
  '라이브니스':   'logs.failure_liveness',
};

/* ── 등록 이미지 플레이스홀더 ── */
function RegisterImagePlaceholder({ src, label }: { src?: string; label: string }) {
  const { t } = useTranslation();
  const [imgErr, setImgErr] = useState(false);
  return (
    <div className="inline-flex flex-col items-center gap-3">
      <div style={{ width: 147, height: 160 }}
        className="relative rounded-[8px] border border-[#64748b] bg-[#e2e8f0] overflow-hidden flex items-center justify-center flex-shrink-0">
        {src && !imgErr ? (
          <img src={src} alt={label} onError={() => setImgErr(true)} className="w-full h-full object-cover" />
        ) : (
          <span className="text-[14px] font-semibold text-[#94a3b8] tracking-[-0.35px]">{t('common.none')}</span>
        )}
      </div>
      <span className="text-[14px] font-semibold text-[#64748b] tracking-[-0.35px] leading-5 whitespace-nowrap">
        {label}
      </span>
    </div>
  );
}

/* ── 메인 컴포넌트 ── */
export default function LogDetailPanel({ entry, onClose }: LogDetailPanelProps) {
  const { t } = useTranslation();
  const success    = isSuccess(entry.result);
  const isRegister = entry.module === '등록';
  const hasImages  = entry.module === '1:1 촬영인증' || entry.module === '1:1 사진인증' || entry.module === '1:N 매칭';
  const hasSimilarity = !isRegister && entry.module !== '라이브니스';

  /* 실패 시 사유 */
  const failureDetail = entry.failureReason || entry.failureType;
  const isLiveness    = entry.module === '라이브니스';

  /* 결과 카드 — 모듈/성공 여부별 레이블·값 */
  const cardLabel = success
    ? (isLiveness ? t('logs.result_title') : (entry.module === '등록' ? t('logs.register_result') : t('logs.match_result')))
    : t(FAILURE_LABEL_KEY[entry.module]);

  const cardValue = success
    ? (isLiveness ? t('logs.liveness_success_detail') : t('logs.success'))
    : (isLiveness
        ? (failureDetail || t('logs.failure_reason_default'))
        : (failureDetail || t('logs.failure')));

  const resultTextColor = success ? '#006fff' : '#ef4444';

  /* 라이브니스 상태 */
  const livenessApplied = entry.checkLiveness === true;
  const livenessNA = entry.checkLiveness === undefined || entry.checkLiveness === null;

  return (
    <div className="fixed inset-0 z-[var(--z-modal)] flex items-center justify-end bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]" onClick={onClose}>
      <div className="relative h-full flex items-center p-[26px]" onClick={e => e.stopPropagation()}>
        <div className="bg-white flex flex-col h-full w-[750px] px-9 py-10 rounded-[34px] shadow-[0_8px_40px_0_rgba(0,0,0,0.12)] overflow-y-auto">

          {/* 헤더 */}
          <div className="flex items-center justify-between mb-6 flex-shrink-0">
            <div className="flex items-center gap-2">
              <h2 className="text-[24px] font-bold text-[#1e293b] tracking-[-0.6px] leading-[1.4]">
                {t('logs.title')}
              </h2>
              {entry.serialNo && (
                <span className="bg-[#f5f6f8] px-3 py-1 rounded-[12px] text-[16px] text-[#334155] tracking-[-0.4px] leading-6">
                  #{entry.serialNo}
                </span>
              )}
            </div>
            <button onClick={onClose} className="w-9 h-9 flex items-center justify-center rounded-full hover:bg-[#f1f5f9] transition-colors text-[#94a3b8]">
              <CloseIcon />
            </button>
          </div>

          {/* 결과 + 유사도 카드 */}
          <div className="flex gap-3 mb-6">
            {/* 결과 카드 */}
            <div className={['flex gap-4 p-5 rounded-[16px] overflow-hidden', hasSimilarity ? 'w-1/2' : 'flex-1', success ? 'bg-[#eff9ff] items-center' : 'bg-[#fff7f6] items-start'].join(' ')}>
              <div className={['w-12 h-12 flex items-center justify-center rounded-[12px] flex-shrink-0', success ? 'bg-[#006fff]' : 'bg-[#ef4444]'].join(' ')}>
                {success ? <CheckIcon /> : <ErrorIcon />}
              </div>
              <div className="flex flex-col gap-1 min-w-0">
                <span className={['text-[14px] tracking-[-0.35px] leading-5', success ? 'font-normal text-[#334155]' : 'font-semibold text-[#475569]'].join(' ')}>
                  {cardLabel}
                </span>
                <span className={['font-semibold tracking-[-0.5px] leading-[1.4] break-words', success ? 'text-[20px]' : 'text-[16px]'].join(' ')} style={{ color: resultTextColor }}>
                  {cardValue}
                </span>
              </div>
            </div>

            {/* 유사도 카드 */}
            {hasSimilarity && (
              <div className="w-1/2 flex items-center gap-4 p-5 rounded-[16px] bg-[#f5f2ff]">
                <div className="w-12 h-12 flex items-center justify-center rounded-[12px] bg-[#8a58ff] flex-shrink-0">
                  <BarChartIcon />
                </div>
                <div className="flex flex-col gap-1 min-w-0">
                  <div className="flex items-center gap-1">
                    <span className="text-[14px] text-[#334155] tracking-[-0.35px] leading-5">{t('logs.score')}</span>
                    <SmHelpIcon />
                  </div>
                  <span className="text-[20px] font-semibold text-[#8a58ff] tracking-[-0.5px] leading-[1.4]">
                    {entry.score !== undefined ? `${entry.score}%` : '-'}
                  </span>
                </div>
              </div>
            )}
          </div>

          {/* 시스템 상세 정보 */}
          <div className="flex flex-col gap-3 mb-6">
            <div className="flex items-center gap-2 text-[#334155]">
              <HistoryIcon />
              <span className="text-[14px] font-semibold tracking-[-0.35px] leading-5">{t('logs.system_detail')}</span>
            </div>
            <div className="bg-[#f9fafc] rounded-[16px] px-5 py-6 flex flex-col gap-5">
              {isLiveness ? (
                /* ── 라이브니스 전용: 액션 + 일시 → 구분선 → 요청 ID ── */
                <>
                  <div className="flex gap-4">
                    <LabelItem icon={<SmActionIcon />} label={t('logs.action')} value={t(MODULE_TO_ACTION[entry.module])} />
                    <LabelItem icon={<SmClockIcon />}  label={t('logs.datetime')} value={entry.createdAt} />
                  </div>
                  <div className="h-px bg-[#cbd5e1]" />
                  <IdBox label={t('logs.request_id')} value={entry.requestId} />
                </>
              ) : (
                /* ── 일반 모듈 ── */
                <>
                  {/* 행 1: 액션 + User ID */}
                  <div className="flex gap-4">
                    <LabelItem icon={<SmActionIcon />} label={t('logs.action')} value={t(MODULE_TO_ACTION[entry.module])} />
                    <LabelItem icon={<SmUserIcon />}   label="User ID"           value={entry.userId ? String(entry.userId) : '-'} />
                  </div>
                  {/* 행 2: 매칭 방식 + 히스토리 ID — 등록 모듈은 미노출 */}
                  {!isRegister && (
                    <div className="flex gap-4">
                      <LabelItem icon={<SmAuthIcon />} label={t('logs.match_type')} value={MODULE_TO_MATCH_TYPE[entry.module]} />
                      <LabelItem icon={<SmFileIcon />} label={t('logs.history_id')} value={entry.serialNo ? String(entry.serialNo) : '-'} />
                    </div>
                  )}
                  {/* 행 3: 라이브니스 검사 + 일시 */}
                  <div className="flex gap-4">
                    <div className="flex flex-col gap-2 flex-1 min-w-0">
                      <div className="flex items-center gap-1.5">
                        <span className="text-[#64748b] flex-shrink-0"><SmActivityIcon /></span>
                        <span className="text-[14px] text-[#64748b] tracking-[-0.35px] leading-5 whitespace-nowrap">{t('logs.liveness_check')}</span>
                      </div>
                      {livenessNA ? (
                        <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-5">-</span>
                      ) : livenessApplied ? (
                        <div className="flex items-center gap-2">
                          <SmCheckCircleIcon />
                          <span className="text-[14px] font-semibold text-[#0fb981] tracking-[-0.35px] leading-5">{t('logs.applied')}</span>
                        </div>
                      ) : (
                        <div className="flex items-center gap-2">
                          <SmXCircleIcon />
                          <span className="text-[14px] font-semibold text-[#f59e0b] tracking-[-0.35px] leading-5">{t('logs.not_applied')}</span>
                        </div>
                      )}
                    </div>
                    <LabelItem icon={<SmClockIcon />} label={t('logs.datetime')} value={entry.createdAt} />
                  </div>
                  {/* 구분선 + 요청 ID / FID — 등록 모듈은 미노출 */}
                  {!isRegister && (
                    <>
                      <div className="h-px bg-[#cbd5e1]" />
                      <div className="flex gap-5">
                        <IdBox label={t('logs.request_id')} value={entry.requestId} />
                        <IdBox label="FID"                  value={entry.fid ?? '-'} />
                      </div>
                    </>
                  )}
                </>
              )}
            </div>
          </div>

          {/* 이미지 섹션 — consentSnapshot false 시 비공개 표시 */}
          {(isRegister || isLiveness || hasImages) && (
            <div className="flex flex-col gap-3 mb-6">
              <div className="flex items-center gap-2 text-[#334155]">
                <GalleryIcon />
                <span className="text-[14px] font-semibold tracking-[-0.35px] leading-5">{t('logs.image')}</span>
              </div>
              <div className="bg-[#f9fafc] rounded-[16px] px-5 py-6 flex items-center justify-center">
                {entry.consentSnapshot === false ? (
                  <div className="flex flex-col items-center gap-2 py-4 text-[#94a3b8]">
                    <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                      <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                    </svg>
                    <span className="text-[13px] tracking-[-0.325px]">{t('common.private')}</span>
                  </div>
                ) : isRegister ? (
                  <RegisterImagePlaceholder
                    src={entry.matchingFaceImagePath || entry.faceImagePath}
                    label={t('logs.register_photo')}
                  />
                ) : isLiveness ? (
                  <RegisterImagePlaceholder
                    src={entry.faceImagePath || entry.matchingFaceImagePath}
                    label={t('logs.register_photo')}
                  />
                ) : (
                  <div className="flex gap-8 items-start">
                    {entry.faceImagePath && (
                      <FaceImage src={entry.faceImagePath} width={86} height={94} label={t('logs.base_face')} />
                    )}
                    {entry.matchingFaceImagePath && (
                      <FaceImage src={entry.matchingFaceImagePath} width={147} height={160} label={success ? t('logs.captured_face_matched') : t('logs.captured_face')} highlight={success} />
                    )}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* 확인 버튼 */}
          <button
            onClick={onClose}
            className="mt-auto w-full h-12 flex items-center justify-center bg-[#f1f5f9] rounded-[8px] text-[18px] font-semibold text-[#334155] tracking-[-0.45px] hover:bg-[#e2e8f0] transition-colors flex-shrink-0"
          >
            {t('common.confirm')}
          </button>
        </div>
      </div>
    </div>
  );
}
