/**
 * FeaturesConsentPage — 특징점 관리 (개인정보 노출 동의 ON)
 *
 * consentEnabled = true 일 때 라우팅됨
 * 기존 FeaturesPage에서 INFO(이름) + 사진 컬럼 추가
 */

import { useState, useRef, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { DashboardLayout } from '@/components/layout';
import { Alert } from '@/components/ui';
import { SearchBlueIcon, RefreshIcon, AuthMethodBadge } from '@/components/ui/icons';
import { Pagination } from '@/components/common';
import { getFeatures, deleteFeature, registerFaceFeature, updateFeature } from '@/services/feature';
import { useProjectContext } from '@/contexts/ProjectContext';
import type { FeatureRow } from '@/services/feature';

/* ── 아이콘 ── */

const TrashIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="3 6 5 6 21 6" />
    <path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6" />
    <path d="M10 11v6M14 11v6" />
    <path d="M9 6V4a1 1 0 011-1h4a1 1 0 011 1v2" />
  </svg>
);

const MoreVertIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <circle cx="12" cy="5"  r="1.5" />
    <circle cx="12" cy="12" r="1.5" />
    <circle cx="12" cy="19" r="1.5" />
  </svg>
);

const ExternalLinkIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
    <polyline points="15 3 21 3 21 9" />
    <line x1="10" y1="14" x2="21" y2="3" />
  </svg>
);

const EditIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
  </svg>
);

const AddIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round">
    <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
  </svg>
);



const UserIcon = () => (
  <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
    <circle cx="12" cy="7" r="4" />
  </svg>
);

/* ── 스타일 상수 ── */
const TH = 'px-4 py-3 text-center text-[14px] font-semibold text-[#475569] tracking-[-0.35px] leading-[1.4] whitespace-nowrap';
const TD = 'px-4 py-2 border-r border-[#e2e8f0] last:border-r-0';

interface RegisterResult {
  preview:  string | null;
  memo:     string;
  faceId:   string;
}

/* ── 사진 썸네일 ── */
function PhotoCell({ src, name }: { src: string; name?: string | null }) {
  const [err, setErr] = useState(false);
  if (err || !src) {
    return (
      <div className="w-[36px] h-[36px] bg-[#e2e8f0] rounded-[4px] flex items-center justify-center flex-shrink-0">
        <UserIcon />
      </div>
    );
  }
  return (
    <img
      src={src}
      alt={name ?? 'face'}
      onError={() => setErr(true)}
      className="w-[36px] h-[36px] object-cover rounded-[4px] flex-shrink-0"
    />
  );
}

/* ── 수동 등록 패널 ── */
function RegisterPanel({ onClose, onSuccess }: { onClose: () => void; onSuccess: (r: RegisterResult) => void }) {
  const { t } = useTranslation();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [file,    setFile]    = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [memo,    setMemo]    = useState('');
  const [view,    setView]    = useState<'register' | 'fail'>('register');

  const handleFileChange = (f: File) => {
    setFile(f);
    setPreview(URL.createObjectURL(f));
  };

  const { mutate: submit, isPending } = useMutation({
    mutationFn: () => registerFaceFeature(file!, { description: memo || undefined }),
    onSuccess: (res) => onSuccess({ preview, memo, faceId: (res.data.data as { featureId?: string }).featureId ?? '' }),
    onError: () => setView('fail'),
  });

  /* ── 실패 뷰 ── */
  if (view === 'fail') {
    return (
      <div className="fixed inset-0 z-[var(--z-modal)]">
        <div className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]" />
        <div className={PANEL_750}>
          <div className="flex flex-col flex-1 justify-between px-9 py-10">
            <div className="flex flex-col gap-[117px] items-center">
              <div className="flex flex-col gap-3 items-center w-full pt-3">
                <div className="bg-[#fff7f6] p-3 rounded-[12px]">
                  <svg width="51" height="51" viewBox="0 0 24 24" fill="none">
                    <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" fill="#ef4444" />
                    <line x1="12" y1="9" x2="12" y2="13" stroke="white" strokeWidth="2" strokeLinecap="round" />
                    <circle cx="12" cy="17" r="1" fill="white" />
                  </svg>
                </div>
                <h2 className="text-[24px] font-semibold text-[#334155] tracking-[-0.6px] leading-[40px] text-center">
                  {t('features.register_fail_title')}
                </h2>
                <p className="text-[18px] font-semibold text-[#94a3b8] tracking-[-0.45px] leading-7 text-center">
                  {t('features.edit_fail_subtitle')}
                </p>
              </div>
              <div className="flex flex-col gap-[22px] items-start w-full px-[52px]">
                <div className="flex items-start justify-between w-full">
                  <span className="text-[16px] font-normal text-[#64748b] tracking-[-0.4px] leading-6">
                    {t('features.edit_photo_label')}
                  </span>
                  <div className="w-[51px] h-[51px] rounded-[8px] overflow-hidden bg-[#e2e8f0] flex-shrink-0">
                    {preview ? (
                      <img src={preview} alt="face" className="w-full h-full object-cover" />
                    ) : (
                      <div className="w-full h-full flex items-center justify-center">
                        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.5" strokeLinecap="round">
                          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
                        </svg>
                      </div>
                    )}
                  </div>
                </div>
                <div className="w-full border-t border-[#e2e8f0]" />
                <div className="flex items-center justify-between w-full">
                  <span className="text-[16px] font-normal text-[#64748b] tracking-[-0.4px] leading-6">
                    INFO ({t('features.col_memo')})
                  </span>
                  <span className="text-[16px] font-semibold text-[#475569] tracking-[-0.4px] leading-6 text-right">
                    {memo || '-'}
                  </span>
                </div>
                <div className="w-full border-t border-[#e2e8f0]" />
              </div>
            </div>
            <div className="flex flex-col gap-3">
              <button
                type="button"
                onClick={() => setView('register')}
                className="h-[48px] w-full rounded-[8px] bg-[#006fff] text-white text-[18px] font-bold tracking-[-0.025px] leading-6 hover:opacity-90 transition-opacity"
              >
                {t('features.edit_fail_retry')}
              </button>
              <button
                type="button"
                onClick={onClose}
                className="h-[48px] w-full rounded-[8px] bg-[#f1f5f9] text-[#334155] text-[18px] font-semibold tracking-[-0.45px] leading-7 hover:bg-[#e2e8f0] transition-colors"
              >
                {t('features.edit_fail_back')}
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  /* ── 등록 뷰 ── */
  return (
    <div className="fixed inset-0 z-[var(--z-modal)]">
      <div className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]" onClick={onClose} />
      <div className={PANEL_750}>
        <div className="flex flex-col flex-1 justify-between px-9 py-10 overflow-y-auto">
          <div className="flex flex-col gap-6">
            {/* 헤더 */}
            <div className="flex items-center justify-between">
              <h2 className="text-[24px] font-bold text-[#334155] tracking-[-0.025px] leading-[1.4]">
                {t('features.register_title')}
              </h2>
              <button
                type="button"
                onClick={onClose}
                className="w-[37px] h-[37px] flex items-center justify-center text-[#94a3b8] hover:text-[#475569] transition-colors"
              >
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </button>
            </div>

            {/* 사진 영역 */}
            <div className="flex flex-col items-center gap-6 p-5 rounded-[16px]">
              <div
                className="relative cursor-pointer group"
                onClick={() => fileInputRef.current?.click()}
              >
                <div className={[
                  'w-[147px] h-[160px] rounded-[20px] overflow-hidden bg-[#e2e8f0] flex-shrink-0',
                  'border-2 shadow-[0px_4px_12px_rgba(0,0,0,0.1)]',
                  file ? 'border-[#2dd4bf]' : 'border-dashed border-[#cbd5e1]',
                ].join(' ')}>
                  {preview ? (
                    <img src={preview} alt="preview" className="w-full h-full object-cover" />
                  ) : (
                    <div className="w-full h-full flex flex-col items-center justify-center gap-2">
                      <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z" />
                        <circle cx="12" cy="13" r="4" />
                      </svg>
                    </div>
                  )}
                </div>
                <div className="absolute inset-0 rounded-[20px] bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                  <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z" />
                    <circle cx="12" cy="13" r="4" />
                  </svg>
                </div>
              </div>
              <p className="text-[15px] font-medium text-[#02aaa4] tracking-[-0.375px] leading-[1.4]">
                {file ? file.name : t('features.register_photo_hint')}
              </p>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                className="hidden"
                onChange={(e) => { const f = e.target.files?.[0]; if (f) handleFileChange(f); }}
              />
            </div>

            {/* 폼 영역 */}
            <div className="bg-[#f9fafc] rounded-[12px] px-5 py-6">
              <div className="flex flex-col gap-3">
                <p className="text-[14px] font-semibold text-[#475569] tracking-[-0.35px] leading-5">
                  INFO ({t('features.col_memo')})
                </p>
                <textarea
                  value={memo}
                  onChange={(e) => setMemo(e.target.value.slice(0, 100))}
                  maxLength={100}
                  placeholder={t('features.register_memo_placeholder')}
                  className={[
                    'h-[101px] w-full px-4 py-3 rounded-[8px] border border-[#e2e8f0] bg-white resize-none',
                    'text-[16px] font-normal text-[#1e293b] tracking-[-0.4px] leading-5',
                    'placeholder:text-[#94a3b8]',
                    'focus:outline-none focus:border-[#006fff] focus:shadow-[0_0_0_3px_rgba(0,111,255,0.1)]',
                    'transition-all',
                  ].join(' ')}
                />
              </div>
            </div>
          </div>

          {/* 등록 버튼 */}
          <button
            type="button"
            disabled={!file || isPending}
            onClick={() => submit()}
            className={[
              'h-[48px] w-full rounded-[8px] text-[18px] font-bold tracking-[-0.025px] leading-6 transition-colors mt-6',
              !file || isPending ? 'bg-[#cbd5e1] text-[#64748b] cursor-not-allowed' : 'bg-[#006fff] text-white hover:opacity-90',
            ].join(' ')}
          >
            {isPending ? t('common.loading') : t('features.register_submit')}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ── 등록 완료 패널 ── */
function RegisterSuccessPanel({ result, onConfirm }: { result: RegisterResult; onConfirm: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="fixed inset-0 z-[var(--z-modal)]">
      <div className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]" />
      <div className={PANEL_750}>
        <div className="flex flex-col flex-1 justify-between px-9 py-10">
          <div className="flex flex-col gap-[117px] items-center">
            {/* 아이콘 + 타이틀 + 부제목 */}
            <div className="flex flex-col items-center gap-7 w-full pt-9">
              <div className="w-[52px] h-[52px] rounded-full bg-[#006fff] flex items-center justify-center flex-shrink-0">
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              </div>
              <div className="flex flex-col items-center gap-2 w-full">
                <h2 className="text-[24px] font-semibold text-[#1e293b] tracking-[-0.6px] leading-[40px] text-center">
                  {t('features.register_success_title')}
                </h2>
                <p className="text-[18px] font-semibold text-[#64748b] tracking-[-0.45px] leading-7 text-center">
                  {t('features.register_success_subtitle')}
                </p>
              </div>
            </div>

            {/* 등록 내용 요약 */}
            <div className="flex flex-col gap-[22px] items-start w-full px-[52px]">
              <div className="flex items-start justify-between w-full">
                <span className="text-[16px] font-normal text-[#64748b] tracking-[-0.4px] leading-5">
                  {t('features.edit_photo_label')}
                </span>
                <div className="w-[51px] h-[51px] rounded-[8px] overflow-hidden bg-[#e2e8f0] flex-shrink-0">
                  {result.preview ? (
                    <img src={result.preview} alt="face" className="w-full h-full object-cover" />
                  ) : (
                    <div className="w-full h-full flex items-center justify-center">
                      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.5" strokeLinecap="round">
                        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
                      </svg>
                    </div>
                  )}
                </div>
              </div>
              <div className="w-full border-t border-[#e2e8f0]" />
              <div className="flex items-center justify-between w-full">
                <span className="text-[16px] font-normal text-[#64748b] tracking-[-0.4px] leading-5">
                  INFO ({t('features.col_memo')})
                </span>
                <span className="text-[16px] font-semibold text-[#475569] tracking-[-0.4px] leading-5 text-right">
                  {result.memo || '-'}
                </span>
              </div>
              <div className="w-full border-t border-[#e2e8f0]" />
            </div>
          </div>

          {/* 확인 버튼 */}
          <button
            type="button"
            onClick={onConfirm}
            className="h-[48px] w-full rounded-[8px] bg-[#006fff] text-white text-[18px] font-bold tracking-[-0.025px] leading-6 hover:opacity-90 transition-opacity"
          >
            {t('common.confirm')}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ── 특징점 상세 패널 ── */
function FeatureDetailPanel({ user, onClose }: { user: FeatureRow; onClose: () => void }) {
  const { t } = useTranslation();
  const [imgErr, setImgErr] = useState(false);

  return createPortal(
    <div className="fixed inset-0 z-[var(--z-modal)]" aria-modal="true" role="dialog">
      <div className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]" onClick={onClose} />
      <div
        className="absolute right-6 top-6 bottom-6 w-[480px] bg-white rounded-[34px] px-9 py-10 flex flex-col gap-6 shadow-[var(--shadow-xl)] overflow-y-auto"
        onClick={e => e.stopPropagation()}
      >
        {/* 헤더 */}
        <div className="flex items-center justify-between flex-shrink-0">
          <h2 className="text-[22px] font-bold text-[#1e293b] tracking-[-0.55px]">
            {t('settings.feature_detail_title')}
          </h2>
          <button onClick={onClose} className="w-9 h-9 flex items-center justify-center rounded-full hover:bg-[#f1f5f9] transition-colors text-[#94a3b8]">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        {/* 사진 */}
        <div className="flex justify-center">
          {user.faceImagePath && !imgErr ? (
            <img
              src={user.faceImagePath}
              alt="face"
              onError={() => setImgErr(true)}
              className="w-[180px] h-[200px] object-cover rounded-[12px] shadow-sm"
            />
          ) : (
            <div className="w-[180px] h-[200px] bg-[#f1f5f9] rounded-[12px] flex items-center justify-center">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.5" strokeLinecap="round">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
              </svg>
            </div>
          )}
        </div>

        {/* 정보 */}
        <div className="flex flex-col gap-4 bg-[#f9fafc] rounded-[16px] px-5 py-5">
          <div className="flex flex-col gap-1">
            <span className="text-[12px] font-medium text-[#94a3b8] tracking-[-0.3px]">FID</span>
            <span className="text-[14px] font-mono text-[#334155] tracking-[-0.35px] break-all">{user.faceId}</span>
          </div>
          <div className="h-px bg-[#e2e8f0]" />
          <div className="flex flex-col gap-1">
            <span className="text-[12px] font-medium text-[#94a3b8] tracking-[-0.3px]">{t('features.col_memo')}</span>
            <span className="text-[15px] text-[#334155] tracking-[-0.375px]">{user.userDescription || '-'}</span>
          </div>
          <div className="h-px bg-[#e2e8f0]" />
          <div className="flex flex-col gap-1">
            <span className="text-[12px] font-medium text-[#94a3b8] tracking-[-0.3px]">{t('features.col_created_at')}</span>
            <span className="text-[15px] text-[#334155] tracking-[-0.375px]">{user.createdAt}</span>
          </div>
        </div>

        {/* 확인 버튼 */}
        <button
          onClick={onClose}
          className="mt-auto w-full h-12 bg-[#006fff] rounded-[10px] text-[15px] font-semibold text-white hover:opacity-90 transition-opacity"
        >
          {t('common.confirm')}
        </button>
      </div>
    </div>,
    document.body
  );
}

/* ── 삭제 확인 다이얼로그 ── */
function DeleteDialog({ user, isDeleting, onConfirm, onCancel }: { user: FeatureRow; isDeleting: boolean; onConfirm: () => void; onCancel: () => void }) {
  const { t } = useTranslation();
  return createPortal(
    <div className="fixed inset-0 z-[var(--z-modal)] flex items-center justify-center">
      <div className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]" onClick={onCancel} />
      <div className="relative z-10 bg-white rounded-[16px] shadow-[0_20px_60px_rgba(0,0,0,0.15)] w-[400px] p-6 flex flex-col gap-5">
        <div className="flex flex-col gap-1.5">
          <h2 className="text-[18px] font-semibold text-[#1e293b] tracking-[-0.45px]">{t('features.delete_dialog_title')}</h2>
          <p className="text-[14px] text-[#475569] tracking-[-0.35px] leading-[1.6]">{t('features.delete_dialog_body')}</p>
        </div>
        <div className="bg-[#f8fafc] rounded-[10px] px-4 py-3 flex flex-col gap-1">
          <div className="flex gap-2 text-[13px] tracking-[-0.325px]">
            <span className="text-[#94a3b8] font-medium w-[40px] shrink-0">FID</span>
            <span className="text-[#334155] font-medium break-all">{user.faceId}</span>
          </div>
          {user.userDescription && (
            <div className="flex gap-2 text-[13px] tracking-[-0.325px]">
              <span className="text-[#94a3b8] font-medium w-[40px] shrink-0">{t('features.col_memo')}</span>
              <span className="text-[#334155] font-medium">{user.userDescription}</span>
            </div>
          )}
        </div>
        <div className="flex gap-2 justify-end">
          <button type="button" onClick={onCancel} disabled={isDeleting} className={['h-[40px] px-5 rounded-[8px] border border-[#cbd5e1]', 'text-[14px] font-medium text-[#475569] tracking-[-0.35px]', 'hover:bg-[#f1f5f9] transition-colors disabled:opacity-50'].join(' ')}>{t('common.cancel')}</button>
          <button type="button" onClick={onConfirm} disabled={isDeleting} className={['h-[40px] px-5 rounded-[8px]', 'bg-[var(--color-entry)] hover:bg-[#c02020]', 'text-[14px] font-semibold text-white tracking-[-0.35px]', 'transition-colors disabled:opacity-50 flex items-center gap-1.5'].join(' ')}>
            {isDeleting ? t('features.deleting') : t('common.delete')}
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
}

const PANEL_750 = [
  'absolute right-6 top-6 bottom-6',
  'w-[750px] bg-white rounded-[34px] flex flex-col',
  'shadow-[0_20px_60px_rgba(0,0,0,0.15)]',
].join(' ');

/* ── 등록정보 수정 패널 ── */
function EditInfoPanel({
  user,
  onClose,
  onSuccess,
}: {
  user: FeatureRow;
  onClose: () => void;
  onSuccess: (updated: FeatureRow) => void;
}) {
  const { t } = useTranslation();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [memo,     setMemo]     = useState(user.userDescription ?? '');
  const [newPhoto, setNewPhoto] = useState<File | null>(null);
  const [preview,  setPreview]  = useState<string | null>(null);
  const [imgErr,   setImgErr]   = useState(false);
  const [view,     setView]     = useState<'edit' | 'fail'>('edit');

  const handlePhotoChange = (file: File) => {
    setNewPhoto(file);
    setPreview(URL.createObjectURL(file));
  };

  const { mutate: save, isPending } = useMutation({
    mutationFn: () => updateFeature(user.featureType, user.featureId, {
      ...(newPhoto ? { featureImage: newPhoto } : {}),
      description: memo,
    }),
    onSuccess: () => onSuccess({ ...user, userDescription: memo }),
    onError: () => setView('fail'),
  });

  const fmtDate = (iso: string) => {
    const d = new Date(iso);
    return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`;
  };

  const photoSrc = preview ?? (imgErr ? null : user.faceImagePath) ?? null;

  /* ── 실패 뷰 ── */
  if (view === 'fail') {
    return (
      <div className="fixed inset-0 z-[var(--z-modal)]">
        <div className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]" />
        <div className={PANEL_750}>
          <div className="flex flex-col flex-1 justify-between px-9 py-10">
            <div className="flex flex-col gap-[117px] items-center">
              {/* 아이콘 + 타이틀 + 부제목 */}
              <div className="flex flex-col gap-3 items-center w-full pt-3">
                <div className="bg-[#fff7f6] p-3 rounded-[12px]">
                  <svg width="51" height="51" viewBox="0 0 24 24" fill="none">
                    <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" fill="#ef4444" />
                    <line x1="12" y1="9" x2="12" y2="13" stroke="white" strokeWidth="2" strokeLinecap="round" />
                    <circle cx="12" cy="17" r="1" fill="white" />
                  </svg>
                </div>
                <h2 className="text-[24px] font-semibold text-[#334155] tracking-[-0.6px] leading-[40px] text-center">
                  {t('features.edit_fail_title')}
                </h2>
                <p className="text-[18px] font-semibold text-[#94a3b8] tracking-[-0.45px] leading-7 text-center">
                  {t('features.edit_fail_subtitle')}
                </p>
              </div>

              {/* 변경 시도 내용 요약 */}
              <div className="flex flex-col gap-[22px] items-start w-full px-[52px]">
                <div className="flex items-start justify-between w-full">
                  <span className="text-[16px] font-normal text-[#64748b] tracking-[-0.4px] leading-6">
                    {t('features.edit_photo_label')}
                  </span>
                  <div className="w-[51px] h-[51px] rounded-[8px] overflow-hidden bg-[#e2e8f0] flex-shrink-0">
                    {photoSrc ? (
                      <img src={photoSrc} alt="face" className="w-full h-full object-cover" />
                    ) : (
                      <div className="w-full h-full flex items-center justify-center">
                        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.5" strokeLinecap="round">
                          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
                        </svg>
                      </div>
                    )}
                  </div>
                </div>
                <div className="w-full border-t border-[#e2e8f0]" />
                <div className="flex items-center justify-between w-full">
                  <span className="text-[16px] font-normal text-[#64748b] tracking-[-0.4px] leading-6">
                    INFO ({t('features.col_memo')})
                  </span>
                  <span className="text-[16px] font-semibold text-[#475569] tracking-[-0.4px] leading-6 text-right">
                    {memo || '-'}
                  </span>
                </div>
                <div className="w-full border-t border-[#e2e8f0]" />
              </div>
            </div>

            {/* 버튼 */}
            <div className="flex flex-col gap-3">
              <button
                type="button"
                onClick={() => setView('edit')}
                className="h-[48px] w-full rounded-[8px] bg-[#006fff] text-white text-[18px] font-bold tracking-[-0.025px] leading-6 hover:opacity-90 transition-opacity"
              >
                {t('features.edit_fail_retry')}
              </button>
              <button
                type="button"
                onClick={onClose}
                className="h-[48px] w-full rounded-[8px] bg-[#f1f5f9] text-[#334155] text-[18px] font-semibold tracking-[-0.45px] leading-7 hover:bg-[#e2e8f0] transition-colors"
              >
                {t('features.edit_fail_back')}
              </button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  /* ── 편집 뷰 ── */
  return (
    <div className="fixed inset-0 z-[var(--z-modal)]">
      <div className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]" onClick={onClose} />
      <div className={PANEL_750}>
        <div className="flex flex-col flex-1 justify-between px-9 py-10 overflow-y-auto">
          {/* 상단 콘텐츠 */}
          <div className="flex flex-col gap-6">
            {/* 헤더 */}
            <div className="flex items-center justify-between">
              <h2 className="text-[24px] font-bold text-[#334155] tracking-[-0.025px] leading-[1.4]">
                {t('features.edit_title')}
              </h2>
              <button
                type="button"
                onClick={onClose}
                className="w-[37px] h-[37px] flex items-center justify-center text-[#94a3b8] hover:text-[#475569] transition-colors"
              >
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </button>
            </div>

            {/* 사진 영역 */}
            <div className="flex flex-col items-center gap-6 p-5 rounded-[16px]">
              <div
                className="relative cursor-pointer group"
                onClick={() => fileInputRef.current?.click()}
              >
                <div className="w-[147px] h-[160px] rounded-[20px] overflow-hidden bg-[#e2e8f0] border-2 border-[#2dd4bf] shadow-[0px_4px_12px_rgba(0,0,0,0.1)] flex-shrink-0">
                  {photoSrc ? (
                    <img src={photoSrc} alt="face" onError={() => setImgErr(true)} className="w-full h-full object-cover" />
                  ) : (
                    <div className="w-full h-full flex items-center justify-center">
                      <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.5" strokeLinecap="round">
                        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
                      </svg>
                    </div>
                  )}
                </div>
                <div className="absolute inset-0 rounded-[20px] bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                  <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z" />
                    <circle cx="12" cy="13" r="4" />
                  </svg>
                </div>
              </div>
              <div className="flex flex-col items-center gap-1">
                <p className="text-[15px] font-medium text-[#02aaa4] tracking-[-0.375px] leading-[1.4]">
                  {t('features.edit_photo_click_hint')}
                </p>
                <p className="text-[13px] font-medium text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">
                  {fmtDate(user.createdAt)} {t('features.edit_photo_updated_at')}
                </p>
              </div>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                className="hidden"
                onChange={(e) => { const f = e.target.files?.[0]; if (f) handlePhotoChange(f); }}
              />
            </div>

            {/* 폼 영역 */}
            <div className="flex flex-col gap-5">
              <div className="bg-[#f9fafc] rounded-[12px] px-5 py-6">
                <div className="flex flex-col gap-3">
                  <p className="text-[14px] font-semibold text-[#475569] tracking-[-0.35px] leading-5">
                    INFO ({t('features.col_memo')})
                  </p>
                  <textarea
                    value={memo}
                    onChange={(e) => setMemo(e.target.value.slice(0, 100))}
                    maxLength={100}
                    placeholder={t('features.register_memo_placeholder')}
                    className={[
                      'h-[101px] w-full px-4 py-3 rounded-[8px] border border-[#e2e8f0] bg-white resize-none',
                      'text-[16px] font-normal text-[#1e293b] tracking-[-0.4px] leading-5',
                      'placeholder:text-[#94a3b8]',
                      'focus:outline-none focus:border-[#006fff] focus:shadow-[0_0_0_3px_rgba(0,111,255,0.1)]',
                      'transition-all',
                    ].join(' ')}
                  />
                </div>
              </div>

              <div className="flex gap-3">
                <div className="flex-1 bg-white border border-[#cbd5e1] rounded-[12px] p-5 flex flex-col gap-2">
                  <p className="text-[14px] font-semibold text-[#475569] tracking-[-0.35px] leading-5">FID</p>
                  <p className="text-[16px] font-normal text-[#64748b] tracking-[-0.4px] leading-5 break-all">{user.faceId}</p>
                </div>
                <div className="flex-1 bg-white border border-[#cbd5e1] rounded-[12px] p-5 flex flex-col gap-2">
                  <p className="text-[14px] font-semibold text-[#475569] tracking-[-0.35px] leading-5">
                    {t('features.edit_date_label')}
                  </p>
                  <p className="text-[16px] font-normal text-[#64748b] tracking-[-0.4px] leading-5">{user.createdAt}</p>
                </div>
              </div>
            </div>
          </div>

          {/* 저장 버튼 */}
          <button
            type="button"
            disabled={isPending}
            onClick={() => save()}
            className={[
              'h-[48px] w-full rounded-[8px] text-[18px] font-bold tracking-[-0.025px] leading-6 transition-colors mt-6',
              isPending ? 'bg-[#cbd5e1] text-[#64748b] cursor-not-allowed' : 'bg-[#006fff] text-white hover:opacity-90',
            ].join(' ')}
          >
            {isPending ? t('common.loading') : t('features.edit_save')}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ── 등록정보 변경 완료 패널 ── */
function EditSuccessPanel({
  user,
  onConfirm,
}: {
  user:      FeatureRow;
  onConfirm: () => void;
}) {
  const { t } = useTranslation();
  const [imgErr, setImgErr] = useState(false);

  return (
    <div className="fixed inset-0 z-[var(--z-modal)]">
      <div className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]" />
      <div className={PANEL_750}>
        <div className="flex flex-col flex-1 justify-between px-9 py-10">
          <div className="flex flex-col gap-[117px] items-center">
            {/* 아이콘 + 타이틀 + 부제목 */}
            <div className="flex flex-col items-center gap-7 w-full pt-9">
              <div className="w-[52px] h-[52px] rounded-full bg-[#006fff] flex items-center justify-center flex-shrink-0">
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              </div>
              <div className="flex flex-col items-center gap-2 w-full">
                <h2 className="text-[24px] font-semibold text-[#1e293b] tracking-[-0.6px] leading-[40px] text-center">
                  {t('features.edit_success_title')}
                </h2>
                <p className="text-[18px] font-semibold text-[#64748b] tracking-[-0.45px] leading-7 text-center">
                  {t('features.edit_success_subtitle')}
                </p>
              </div>
            </div>

            {/* 변경 내용 요약 */}
            <div className="flex flex-col gap-[22px] items-start w-full px-[52px]">
              <div className="flex items-start justify-between w-full">
                <span className="text-[16px] font-normal text-[#64748b] tracking-[-0.4px] leading-5">
                  {t('features.edit_photo_label')}
                </span>
                <div className="w-[51px] h-[51px] rounded-[8px] overflow-hidden bg-[#e2e8f0] flex-shrink-0">
                  {!imgErr && user.faceImagePath ? (
                    <img src={user.faceImagePath} alt="face" onError={() => setImgErr(true)} className="w-full h-full object-cover" />
                  ) : (
                    <div className="w-full h-full flex items-center justify-center">
                      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.5" strokeLinecap="round">
                        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
                      </svg>
                    </div>
                  )}
                </div>
              </div>
              <div className="w-full border-t border-[#e2e8f0]" />
              <div className="flex items-center justify-between w-full">
                <span className="text-[16px] font-normal text-[#64748b] tracking-[-0.4px] leading-5">
                  INFO ({t('features.col_memo')})
                </span>
                <span className="text-[16px] font-semibold text-[#475569] tracking-[-0.4px] leading-5 text-right">
                  {user.userDescription || '-'}
                </span>
              </div>
              <div className="w-full border-t border-[#e2e8f0]" />
            </div>
          </div>

          {/* 확인 버튼 */}
          <button
            type="button"
            onClick={onConfirm}
            className="h-[48px] w-full rounded-[8px] bg-[#006fff] text-white text-[18px] font-bold tracking-[-0.025px] leading-6 hover:opacity-90 transition-opacity"
          >
            {t('common.confirm')}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ── 작업 드롭다운 ── */
function ActionMenu({ user, onDelete, onEdit }: { user: FeatureRow; onDelete: () => void; onEdit: () => void }) {
  const { t }       = useTranslation();
  const navigate    = useNavigate();
  const [open, setOpen] = useState(false);
  const btnRef = useRef<HTMLButtonElement>(null);
  const [pos, setPos] = useState({ top: 0, right: 0 });

  const openMenu = () => {
    if (btnRef.current) {
      const rect = btnRef.current.getBoundingClientRect();
      setPos({
        top:   rect.bottom + window.scrollY + 4,
        right: window.innerWidth - rect.right,
      });
    }
    setOpen(true);
  };

  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (btnRef.current && !btnRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  const menuItems = (
    <div
      className="fixed z-[9999] w-[200px] bg-white rounded-[12px] shadow-[0px_8px_32px_rgba(0,0,0,0.12)] border border-[#e2e8f0] overflow-hidden py-2"
      style={{ top: pos.top, right: pos.right }}
      onMouseDown={e => e.stopPropagation()}
    >
      <button
        onClick={() => { setOpen(false); navigate(`/dashboard/logs?fid=${encodeURIComponent(user.faceId)}`); }}
        className="w-full flex items-center gap-3 px-5 py-3.5 text-[16px] font-medium text-[#334155] tracking-[-0.4px] hover:bg-[#f8fafc] transition-colors"
      >
        <span className="text-[#475569]"><ExternalLinkIcon /></span>
        {t('features.action_view_log')}
      </button>
      <button
        onClick={() => { setOpen(false); onEdit(); }}
        className="w-full flex items-center gap-3 px-5 py-3.5 text-[16px] font-medium text-[#334155] tracking-[-0.4px] hover:bg-[#f8fafc] transition-colors"
      >
        <span className="text-[#475569]"><EditIcon /></span>
        {t('features.action_edit')}
      </button>
      <div className="mx-4 my-1 border-t border-[#e2e8f0]" />
      <button
        onClick={() => { setOpen(false); onDelete(); }}
        className="w-full flex items-center gap-3 px-5 py-3.5 text-[16px] font-medium text-[var(--color-entry)] tracking-[-0.4px] hover:bg-[var(--color-entry-bg)] transition-colors"
      >
        <TrashIcon />
        {t('features.action_delete')}
      </button>
    </div>
  );

  return (
    <>
      <button
        ref={btnRef}
        onClick={openMenu}
        className="p-1.5 rounded-[6px] text-[#475569] hover:bg-[#f1f5f9] transition-colors"
      >
        <MoreVertIcon />
      </button>
      {open && createPortal(menuItems, document.body)}
    </>
  );
}

/* ── 메인 컴포넌트 ── */
export default function FeaturesConsentPage() {
  const { t }               = useTranslation();
  const queryClient         = useQueryClient();
  const { selectedProject } = useProjectContext();

  const [searchInput,  setSearchInput]  = useState('');
  /* 인증방식 탭 (기본: 모든 방식) — 얼굴/손바닥 필터는 백엔드 modality 지원 시 연결 */
  const [authTab, setAuthTab] = useState<'all' | 'face' | 'palm'>('all');
  const [searchQuery,  setSearchQuery]  = useState('');
  const [page,         setPage]         = useState(1);
  const [pageSize, setPageSize] = useState(10);

  const [detailTarget,     setDetailTarget]     = useState<FeatureRow | null>(null);
  const [panelOpen,        setPanelOpen]        = useState(false);
  const [registerResult,   setRegisterResult]   = useState<RegisterResult | null>(null);
  const [deleteTarget,     setDeleteTarget]     = useState<FeatureRow | null>(null);
  const [deleteAlert,      setDeleteAlert]      = useState(false);
  const [deleteError,      setDeleteError]      = useState(false);
  const [editTarget,       setEditTarget]       = useState<FeatureRow | null>(null);
  const [editSuccess,      setEditSuccess]      = useState<FeatureRow | null>(null);

  const { data, isLoading } = useQuery({
    queryKey:  ['features', selectedProject?.id, page, pageSize, searchQuery, authTab],
    queryFn:   () => getFeatures({ page, pageSize, ...(authTab !== 'all' ? { featureType: authTab === 'palm' ? 'PALM' : 'FACE' } : {}), ...(searchQuery ? { keyword: searchQuery } : {}) }),
    enabled:   !!selectedProject?.apiKey,
    staleTime: 0,
  });

  const users      = data?.content ?? [];
  const totalPages = data?.page.totalPages ?? 1;
  const totalCount = data?.page.totalElements ?? 0;

  const { mutate: doDelete, isPending: isDeleting } = useMutation({
    mutationFn: (row: FeatureRow) => deleteFeature(row.featureType, row.featureId),
    onSuccess:  () => {
      queryClient.invalidateQueries({ queryKey: ['features'] });
      setDeleteTarget(null);
      setDeleteAlert(true);
    },
    onError: () => { setDeleteTarget(null); setDeleteError(true); },
  });

  const applySearch = () => { setSearchQuery(searchInput.trim()); setPage(1); };

  return (
    <DashboardLayout>
      <div className="flex flex-col gap-5">
        {/* 페이지 타이틀 */}
        <h1 className="text-[26px] font-semibold text-[#1e293b] tracking-[-0.65px] leading-[1.4]">
          {t('nav.features')}
        </h1>

        {/* 인증방식 탭 (모든 방식 / 얼굴 / 손바닥) */}
        <div className="flex items-center gap-4 border-b border-[#cbd5e1]">
          {([
            { id: 'all',  label: t('logs.tab_all') },
            { id: 'face', label: t('auth_type.face') },
            { id: 'palm', label: t('auth_type.palm') },
          ] as const).map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => { setAuthTab(tab.id); setPage(1); }}
              className={[
                'px-3 py-2 text-[16px] leading-[24px] tracking-[-0.4px] border-b-2 -mb-px transition-colors',
                authTab === tab.id
                  ? 'border-[var(--color-link-blue)] text-[var(--color-link-blue)] font-semibold'
                  : 'border-transparent text-[#94a3b8] font-normal hover:text-[#475569]',
              ].join(' ')}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* 툴바 */}
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            {/* 검색 */}
            <div className="flex items-center h-[44px] px-3 rounded-[8px] w-[300px] bg-white border border-[#cbd5e1] focus-within:border-[var(--color-border-focus)] transition-colors">
              <input
                type="text"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') applySearch(); }}
                placeholder={t('features.search_placeholder')}
                className="flex-1 text-[14px] font-medium text-[#1e293b] tracking-[-0.35px] placeholder:text-[#94a3b8] bg-transparent focus:outline-none"
              />
            </div>
            {/* 검색 버튼 — 아웃라인 + 검색 아이콘 */}
            <button
              type="button"
              onClick={applySearch}
              className="h-[44px] inline-flex items-center gap-1 px-3 rounded-[8px] bg-white border border-[#006fff] text-[#006fff] text-[14px] font-semibold tracking-[-0.35px] hover:bg-[#eff9ff] transition-colors whitespace-nowrap"
            >
              <SearchBlueIcon size={18} />
              {t('common.search')}
            </button>
            {/* 초기화 버튼 — refresh 아이콘 */}
            <button
              type="button"
              title={t('common.reset')}
              onClick={() => { setSearchInput(''); setSearchQuery(''); setPage(1); }}
              className="h-[44px] w-[44px] flex items-center justify-center rounded-[8px] border border-[#cbd5e1] bg-white hover:bg-[#f1f5f9] transition-colors"
            >
              <RefreshIcon size={20} />
            </button>
          </div>
          {/* 수동 등록 버튼 — 얼굴만 가능, 손바닥 탭에서는 숨김 */}
          {authTab !== 'palm' && (
            <button
              type="button"
              onClick={() => setPanelOpen(true)}
              className="flex items-center gap-1.5 h-[44px] pl-2 pr-4 rounded-[8px] bg-[#006fff] hover:opacity-90 transition-opacity"
            >
              <AddIcon />
              <span className="text-[14px] font-semibold text-white tracking-[-0.35px]">{t('features.register_btn')}</span>
            </button>
          )}
        </div>

        {/* 테이블 */}
        <div className="w-full overflow-x-auto">
          <table className="w-full border-collapse">
            <thead>
              <tr className="border-b-[2px] border-[#1e293b]">
                <th className="hidden" />
                <th className={TH} style={{ width: 70 }}>{t('logs.serial_no')}</th>
                <th className={TH} style={{ width: 110 }}>{t('projects.col_auth_method')}</th>
                <th className={TH} style={{ width: 368 }}>{t('features.col_memo')}</th>
                <th className={TH} style={{ width: 80 }}>{t('features.col_photo')}</th>
                <th className={TH}>FID</th>
                <th className={TH}>{t('features.col_created_at')}</th>
                <th className={TH} style={{ width: 120 }}>{t('features.col_action')}</th>
              </tr>
            </thead>
            <tbody>
              {isLoading && (
                <tr><td colSpan={7} className="px-4 py-8 text-center text-[14px] text-[#94a3b8]">{t('common.loading')}</td></tr>
              )}
              {!isLoading && users.length === 0 && (
                <tr><td colSpan={7} className="px-4 py-8 text-center text-[14px] text-[#94a3b8]">{t('common.no_data')}</td></tr>
              )}
              {users.map((user) => (
                <tr key={user.featureId} className="border-b border-[#cbd5e1] hover:bg-[#f8fafc] transition-colors cursor-pointer" onClick={() => setDetailTarget(user)}>
                  <td className="hidden" />
                  {/* 일련번호 */}
                  <td className={`${TD} text-center text-[15px] text-[#334155] tracking-[-0.375px]`}>
                    {user.featureId}
                  </td>
                  {/* 인증 방식 */}
                  <td className={`${TD} text-center`}>
                    <AuthMethodBadge palm={user.featureType === 'PALM'} label={user.featureType === 'PALM' ? t('auth_type.palm_short') : t('auth_type.face_short')} />
                  </td>
                  {/* INFO (이름/메모) */}
                  <td className={TD}>
                    <span className="text-[15px] text-[#334155] tracking-[-0.375px] leading-6">
                      {user.userDescription || '-'}
                    </span>
                  </td>
                  {/* 사진 */}
                  <td className={TD}>
                    <PhotoCell src={user.faceImagePath} name={user.userDescription} />
                  </td>
                  {/* FID */}
                  <td className={`${TD} text-center`}>
                    <span className="text-[15px] font-mono text-[#334155] tracking-[-0.375px] leading-6 break-all">
                      {user.faceId}
                    </span>
                  </td>
                  {/* 등록 일시 */}
                  <td className={`${TD} text-center`}>
                    <span className="text-[15px] text-[#334155] tracking-[-0.375px] leading-6 whitespace-nowrap">
                      {user.createdAt}
                    </span>
                  </td>
                  {/* 작업 */}
                  <td className={`${TD} text-center`} onClick={e => e.stopPropagation()}>
                    <ActionMenu user={user} onDelete={() => setDeleteTarget(user)} onEdit={() => setEditTarget(user)} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* 페이지네이션 */}
        <Pagination
          pageNum={page}
          pageSize={pageSize}
          totalPages={totalPages}
          totalElements={totalCount}
          currentCount={users.length}
          onPageChange={setPage}
          onPageSizeChange={(size) => { setPageSize(size); setPage(1); }}
        />
      </div>

      {/* 수동 등록 패널 */}
      {panelOpen && (
        <RegisterPanel
          onClose={() => setPanelOpen(false)}
          onSuccess={(result) => { setPanelOpen(false); setRegisterResult(result); queryClient.invalidateQueries({ queryKey: ['features'] }); }}
        />
      )}

      {/* 등록 완료 패널 */}
      {registerResult && (
        <RegisterSuccessPanel result={registerResult} onConfirm={() => setRegisterResult(null)} />
      )}

      {/* 특징점 상세 패널 */}
      {detailTarget && (
        <FeatureDetailPanel
          user={detailTarget}
          onClose={() => setDetailTarget(null)}
        />
      )}

      {/* 삭제 확인 다이얼로그 */}
      {deleteTarget && (
        <DeleteDialog
          user={deleteTarget}
          isDeleting={isDeleting}
          onConfirm={() => doDelete(deleteTarget)}
          onCancel={() => setDeleteTarget(null)}
        />
      )}

      {/* 토스트 */}
      {deleteAlert && <Alert message={t('features.delete_success')} variant="success" onClose={() => setDeleteAlert(false)} />}
      {deleteError && <Alert message={t('features.delete_error')}   variant="error"   onClose={() => setDeleteError(false)} />}

      {/* 등록정보 수정 패널 */}
      {editTarget && !editSuccess && (
        <EditInfoPanel
          user={editTarget}
          onClose={() => setEditTarget(null)}
          onSuccess={(updated) => {
            queryClient.invalidateQueries({ queryKey: ['features'] });
            setEditTarget(null);
            setEditSuccess(updated);
          }}
        />
      )}

      {/* 등록정보 변경 완료 패널 */}
      {editSuccess && (
        <EditSuccessPanel
          user={editSuccess}
          onConfirm={() => setEditSuccess(null)}
        />
      )}
    </DashboardLayout>
  );
}
