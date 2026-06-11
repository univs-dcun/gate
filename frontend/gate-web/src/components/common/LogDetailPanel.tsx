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

/* ── Figma 아이콘 (public/icons/detail) ── */
const DIcon = ({ name, size = 16, className }: { name: string; size?: number; className?: string }) => (
  <img
    src={`/icons/detail/ic-detail-${name}.svg`}
    width={size}
    height={size}
    className={className}
    style={{ width: size, height: size, objectFit: 'contain', display: 'block' }}
    alt=""
    aria-hidden
  />
);

/* 인증방식 카드 — 얼굴(블루) / 손바닥(퍼플) */
function AuthMethodCard({ palm, label, value }: { palm: boolean; label: string; value: string }) {
  const c = palm
    ? { bg: '#f5f2ff', box: '#8a58ff', text: '#8a58ff' }
    : { bg: '#eff9ff', box: '#006fff', text: '#006fff' };
  return (
    <div className="flex-1 flex items-center gap-3 p-5 rounded-[16px] overflow-hidden" style={{ backgroundColor: c.bg }}>
      <div className="w-12 h-12 flex items-center justify-center rounded-[12px] flex-shrink-0" style={{ backgroundColor: c.box }}>
        <DIcon name={palm ? 'palm' : 'faceid'} size={24} />
      </div>
      <div className="flex flex-col gap-1 min-w-0">
        <span className="text-[14px] text-[#334155] tracking-[-0.35px] leading-5">{label}</span>
        <span className="text-[20px] font-semibold tracking-[-0.5px] leading-[1.4]" style={{ color: c.text }}>
          {value}
        </span>
      </div>
    </div>
  );
}

/* ── 작은 라벨 아이콘 ── */
const LabelItem = ({ icon, label, value, valueColor }: { icon: React.ReactNode; label: string; value: React.ReactNode; valueColor?: string }) => (
  <div className="flex flex-col gap-2 flex-1 min-w-0">
    <div className="flex items-center gap-1.5">
      <span className="flex-shrink-0">{icon}</span>
      <span className="text-[14px] text-[#64748b] tracking-[-0.35px] leading-5 whitespace-nowrap">{label}</span>
    </div>
    <span className={['text-[14px] font-semibold tracking-[-0.35px] leading-5', valueColor ?? 'text-[#334155]'].join(' ')}>
      {value}
    </span>
  </div>
);

/* ── 이미지 셀 ── */
function FaceImage({ src, width, height, label, highlight }: { src?: string; width: number; height: number; label: string; highlight?: boolean }) {
  const [err, setErr] = useState(false);
  return (
    <div className={['inline-flex flex-col items-center gap-2', highlight ? 'rounded-[20px] overflow-hidden bg-[#1e293b]' : ''].join(' ')}>
      <div style={{ width, height }} className={['relative overflow-hidden rounded-[8px] bg-[#e2e8f0] flex-shrink-0', highlight ? 'border border-[#1e293b]' : ''].join(' ')}>
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

  const resultTextColor = success ? '#0fb981' : '#ef4444';

  /* 라이브니스 상태 */
  const livenessApplied = entry.checkLiveness === true;

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
            <button onClick={onClose} className="w-9 h-9 flex items-center justify-center rounded-full hover:bg-[#f1f5f9] transition-colors">
              <DIcon name="close" size={24} />
            </button>
          </div>

          {/* 인증방식 + 결과 + 유사도 카드 */}
          <div className="flex gap-3 mb-6">
            {/* 인증방식 카드 (얼굴/손바닥) */}
            <AuthMethodCard
              palm={entry.authMethod === 'PALM'}
              label={t('projects.col_auth_method')}
              value={entry.authMethod === 'PALM' ? t('auth_type.palm_short') : t('auth_type.face_short')}
            />

            {/* 결과 카드 — 성공=그린 / 실패=레드 */}
            <div className={['flex-1 flex gap-3 p-5 rounded-[16px] overflow-hidden', success ? 'bg-[#f4fcfb] items-center' : 'bg-[#fff7f6] items-start'].join(' ')}>
              {success ? (
                <DIcon name="result-success" size={48} className="flex-shrink-0" />
              ) : (
                <div className="w-12 h-12 flex items-center justify-center rounded-[12px] flex-shrink-0 bg-[#ef4444]">
                  <DIcon name="error" size={24} />
                </div>
              )}
              <div className="flex flex-col gap-1 min-w-0">
                <span className={['text-[14px] tracking-[-0.35px] leading-5', success ? 'font-normal text-[#334155]' : 'font-semibold text-[#475569]'].join(' ')}>
                  {cardLabel}
                </span>
                <span className={['font-semibold tracking-[-0.5px] leading-[1.4] break-words', success ? 'text-[20px]' : 'text-[16px]'].join(' ')} style={{ color: resultTextColor }}>
                  {cardValue}
                </span>
              </div>
            </div>

            {/* 유사도 카드 — 회색(neutral) */}
            {hasSimilarity && (
              <div className="flex-1 flex items-center gap-3 p-5 rounded-[16px] bg-[#f1f5f9]">
                <div className="w-12 h-12 flex items-center justify-center rounded-[12px] bg-[#334155] flex-shrink-0">
                  <DIcon name="barchart" size={24} />
                </div>
                <div className="flex flex-col gap-1 min-w-0">
                  <div className="flex items-center gap-1">
                    <span className="text-[14px] text-[#334155] tracking-[-0.35px] leading-5">{t('logs.score')}</span>
                    <DIcon name="help" size={16} />
                  </div>
                  <span className="text-[20px] font-semibold text-[#334155] tracking-[-0.5px] leading-[1.4]">
                    {entry.score !== undefined ? `${entry.score.toFixed(1)}%` : '-'}
                  </span>
                </div>
              </div>
            )}
          </div>

          {/* 시스템 상세 정보 */}
          <div className="flex flex-col gap-3 mb-6">
            <div className="flex items-center gap-2 text-[#334155]">
              <DIcon name="history" size={16} />
              <span className="text-[14px] font-semibold tracking-[-0.35px] leading-5">{t('logs.system_detail')}</span>
            </div>
            <div className="bg-[#f9fafc] rounded-[16px] px-5 py-6 flex flex-col gap-5">
              {isLiveness ? (
                /* ── 라이브니스 전용: 액션 + 일시 → 구분선 → 요청 ID ── */
                <>
                  <div className="flex gap-4">
                    <LabelItem icon={<DIcon name="action" />} label={t('logs.action')} value={t(MODULE_TO_ACTION[entry.module])} />
                    <LabelItem icon={<DIcon name="clock" />}  label={t('logs.datetime')} value={entry.createdAt} />
                  </div>
                  <div className="h-px bg-[#cbd5e1]" />
                  <IdBox label={t('logs.request_id')} value={entry.requestId} />
                </>
              ) : (
                /* ── 일반 모듈 ── */
                <>
                  {/* 행 1: 액션 + User ID */}
                  <div className="flex gap-4">
                    <LabelItem icon={<DIcon name="action" />} label={t('logs.action')} value={t(MODULE_TO_ACTION[entry.module])} />
                    <LabelItem icon={<DIcon name="user" />}   label="Feature Seq"       value={entry.featureSeq ? String(entry.featureSeq) : '-'} />
                  </div>
                  {/* 행 2: 매칭 방식 + 히스토리 ID — 등록 모듈은 미노출 */}
                  {!isRegister && (
                    <div className="flex gap-4">
                      <LabelItem icon={<DIcon name="authentication" />} label={t('logs.match_type')} value={MODULE_TO_MATCH_TYPE[entry.module]} />
                      <LabelItem icon={<DIcon name="filefind" />} label={t('logs.history_id')} value={entry.serialNo ? String(entry.serialNo) : '-'} />
                    </div>
                  )}
                  {/* 행 3: 라이브니스 검사 + 일시 */}
                  <div className="flex gap-4">
                    <div className="flex flex-col gap-2 flex-1 min-w-0">
                      <div className="flex items-center gap-1.5">
                        <span className="flex-shrink-0"><DIcon name="activity" size={16} /></span>
                        <span className="text-[14px] text-[#64748b] tracking-[-0.35px] leading-5 whitespace-nowrap">{t('logs.liveness_check')}</span>
                      </div>
                      {/* 라이브니스 검사 결과: 적용=성공(그린+체크) / 그 외=미적용(다크, 아이콘 없음) */}
                      {livenessApplied ? (
                        <div className="flex items-center gap-2">
                          <DIcon name="check" size={16} />
                          <span className="text-[14px] font-semibold text-[#0fb981] tracking-[-0.35px] leading-5">{t('logs.success')}</span>
                        </div>
                      ) : (
                        <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-5">{t('logs.not_applied')}</span>
                      )}
                    </div>
                    <LabelItem icon={<DIcon name="clock" />} label={t('logs.datetime')} value={entry.createdAt} />
                  </div>
                  {/* 행 4: 메모 (식별/설명 정보) — 전체 폭, 긴 텍스트 줄바꿈 */}
                  <div className="flex">
                    <LabelItem icon={<DIcon name="action" />} label={t('logs.memo')} value={entry.memo || '-'} />
                  </div>
                  {/* 구분선 + 요청 ID / Feature ID — 등록 모듈은 미노출 */}
                  {!isRegister && (
                    <>
                      <div className="h-px bg-[#cbd5e1]" />
                      <div className="flex gap-5">
                        <IdBox label={t('logs.request_id')} value={entry.requestId} />
                        <IdBox label="Feature ID"                  value={entry.fid ?? '-'} />
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
                <DIcon name="gallery" size={16} />
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
                    label={t('logs.realtime_capture')}
                  />
                ) : isLiveness ? (
                  <RegisterImagePlaceholder
                    src={entry.faceImagePath || entry.matchingFaceImagePath}
                    label={t('logs.realtime_capture')}
                  />
                ) : (
                  <div className="flex gap-8 items-start">
                    {entry.faceImagePath && (
                      <FaceImage src={entry.faceImagePath} width={86} height={94} label={t('logs.base_face')} />
                    )}
                    {entry.matchingFaceImagePath && (
                      <FaceImage src={entry.matchingFaceImagePath} width={147} height={160} label={t('logs.realtime_capture')} highlight />
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
