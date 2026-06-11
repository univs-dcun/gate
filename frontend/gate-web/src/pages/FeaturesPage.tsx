/**
 * FeaturesPage — 특징점 관리
 *
 * Figma node 412-6485 기반
 * API: GET /v1/users (faceId, page, pageSize)
 *      DELETE /v1/users/:userId
 */

import { useState, useRef, useCallback, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { DashboardLayout } from '@/components/layout';
import { Alert } from '@/components/ui';
import { SearchBlueIcon, RefreshIcon, AuthMethodBadge } from '@/components/ui/icons';
import { Pagination } from '@/components/common';
import { getFeatures, deleteFeature, registerFaceFeature } from '@/services/feature';
import { useProjectContext } from '@/contexts/ProjectContext';
import type { FeatureRow } from '@/services/feature';

/* ── 날짜: 서버 응답 그대로 ── */
function formatDate(iso: string) {
  return iso;
}

/* ── 아이콘 ── */
const SortIcon = () => (
  <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
    <path d="M8 3l-3.5 5h7L8 3z" fill="#94A3B8" />
    <path d="M8 13l-3.5-5h7L8 13z" fill="#94A3B8" />
  </svg>
);

const LogIcon = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
    <polyline points="15 3 21 3 21 9" />
    <line x1="10" y1="14" x2="21" y2="3" />
  </svg>
);

const TrashIcon = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="3 6 5 6 21 6" />
    <path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6" />
    <path d="M10 11v6M14 11v6" />
    <path d="M9 6V4a1 1 0 011-1h4a1 1 0 011 1v2" />
  </svg>
);

/* ── 스타일 상수 ── */
const TH = 'px-4 py-3 text-center text-[14px] font-semibold text-[#475569] tracking-[-0.35px] leading-[1.4] whitespace-nowrap sticky-th';
const TD = 'px-4 py-[14px] border-r border-[#e2e8f0] last:border-r-0';

/* ── 클라우드 업 아이콘 ── */
const CloudUpIcon = () => (
  <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="#006fff" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="16 16 12 12 8 16" />
    <line x1="12" y1="12" x2="12" y2="21" />
    <path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3" />
  </svg>
);

const PaperclipIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" />
  </svg>
);

const CheckCircleIcon = () => (
  <svg width="52" height="52" viewBox="0 0 52 52" fill="none">
    <circle cx="26" cy="26" r="26" fill="#006fff" />
    <polyline points="14 26 22 34 38 18" stroke="white" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

/* ── 수동 등록 패널 ── */
interface RegisterResult {
  fileName: string;
  memo:     string;
  faceId:   string;
}

const PANEL_STYLE = [
  'absolute right-6 top-6 bottom-6',
  'w-[480px] bg-white',
  'rounded-[34px] flex flex-col',
].join(' ');

function RegisterPanel({
  onClose,
  onSuccess,
}: {
  onClose:   () => void;
  onSuccess: (result: RegisterResult) => void;
}) {
  const { t }                  = useTranslation();
  const fileInputRef            = useRef<HTMLInputElement>(null);
  const [file, setFile]         = useState<File | null>(null);
  const [memo, setMemo]         = useState('');
  const [isDragging, setIsDragging] = useState(false);
  const [regError, setRegError] = useState('');

  const { mutate: submit, isPending } = useMutation({
    mutationFn: () => registerFaceFeature(file!, { description: memo || undefined }),
    onSuccess: (res) => {
      onSuccess({
        fileName: file!.name,
        memo,
        faceId:   (res.data.data as { featureId?: string }).featureId ?? '',
      });
    },
    onError: (err: unknown) => {
      const status = (err as { response?: { status?: number } })?.response?.status;
      setRegError(status ? `${t('features.register_error')} (${status})` : t('features.register_error'));
    },
  });

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    const dropped = e.dataTransfer.files[0];
    if (dropped) setFile(dropped);
  }, []);

  const handleDragOver = (e: React.DragEvent) => { e.preventDefault(); setIsDragging(true); };
  const handleDragLeave = () => setIsDragging(false);

  return (
    <div className="fixed inset-0 z-[var(--z-modal)]">
      {/* 오버레이 */}
      <div className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]" onClick={onClose} />
      {/* 패널 */}
      <div className={PANEL_STYLE}>
        {/* 스크롤 가능 콘텐츠 */}
        <div className="flex flex-col flex-1 overflow-y-auto px-9 pt-[52px]">
          {/* 배지 */}
          <p className="text-[#006fff] text-[18px] font-semibold tracking-[-0.45px] leading-[1.4] mb-[10px]">
            {t('features.register_badge')}
          </p>
          {/* 제목 */}
          <h2 className="text-[#1e293b] text-[24px] font-bold tracking-[-0.6px] leading-[1.4]">
            {t('features.register_title')}
          </h2>
          <p className="text-[#1e293b] text-[16px] font-normal tracking-[-0.4px] leading-[1.8] mt-1 mb-[32px]">
            {t('features.register_subtitle')}
          </p>

          {/* 드롭존 */}
          <div
            onDrop={handleDrop}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            className={[
              'flex flex-col items-center gap-2 py-8 px-4 rounded-[8px]',
              'border border-dashed',
              isDragging
                ? 'bg-[#dbeeff] border-[#006fff]'
                : 'bg-[#eff9ff] border-[#006fff]',
              'transition-colors',
            ].join(' ')}
          >
            <CloudUpIcon />
            <p className="text-[#1e293b] text-[14px] font-semibold tracking-[-0.35px] leading-[1.4]">
              {t('features.register_drop_hint')}
            </p>
            <p className="text-[13px] text-[#17191a] tracking-[-0.325px] leading-[1.4]">
              {t('features.register_or')}
            </p>
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="flex items-center gap-1 h-[40px] px-3 rounded-[8px] border border-[#cbd5e1] bg-white text-[14px] font-medium text-[#17191a] tracking-[-0.35px] hover:bg-[#f8fafc] transition-colors"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
              </svg>
              {t('features.register_upload_btn')}
            </button>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={(e) => { const f = e.target.files?.[0]; if (f) setFile(f); }}
            />
          </div>

          {/* 파일 정보 */}
          <div className="flex items-center gap-1 mt-3 text-[#475569] text-[14px] font-semibold tracking-[-0.35px]">
            <PaperclipIcon />
            <span>{file ? file.name : t('features.register_no_file')}</span>
          </div>

          {/* 메모 */}
          <div className="flex flex-col gap-2 mt-[28px]">
            <label className="text-[#334155] text-[14px] font-semibold tracking-[-0.35px] leading-[1.4]">
              {t('features.register_memo_label')}
            </label>
            <input
              type="text"
              value={memo}
              onChange={(e) => setMemo(e.target.value)}
              maxLength={100}
              placeholder={`${t('features.register_memo_placeholder')}(${memo.length}/100)`}
              className={[
                'h-[56px] w-full px-4 rounded-[8px] border border-[#e8eef2] bg-white',
                'text-[16px] font-medium text-[#1e293b] tracking-[-0.4px]',
                'placeholder:text-[#94a3b8]',
                'focus:outline-none focus:border-[#006fff] focus:shadow-[0_0_0_3px_rgba(0,111,255,0.1)]',
                'transition-all',
              ].join(' ')}
            />
          </div>

          {/* 오류 */}
          {regError && (
            <p className="mt-2 text-[13px] text-[var(--color-entry)] tracking-[-0.325px]">{regError}</p>
          )}
        </div>

        {/* 버튼 영역 */}
        <div className="flex flex-col gap-[12px] px-9 pb-[52px] mt-8">
          <button
            type="button"
            disabled={!file || isPending}
            onClick={() => { setRegError(''); submit(); }}
            className={[
              'h-[48px] w-full rounded-[8px] text-[18px] font-semibold tracking-[-0.45px] transition-colors',
              !file || isPending
                ? 'bg-[#cbd5e1] text-[#64748b] cursor-not-allowed'
                : 'bg-[#006fff] text-white hover:bg-[#0055cc]',
            ].join(' ')}
          >
            {isPending ? t('features.register_submitting') : t('features.register_submit')}
          </button>
          <button
            type="button"
            onClick={onClose}
            disabled={isPending}
            className="h-[48px] w-full rounded-[8px] bg-[#f1f5f9] text-[18px] font-semibold text-[#1e293b] tracking-[-0.45px] hover:bg-[#e2e8f0] transition-colors disabled:opacity-50"
          >
            {t('common.close')}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ── 수동 등록 완료 패널 ── */
function RegisterSuccessPanel({
  result,
  onConfirm,
}: {
  result:    RegisterResult;
  onConfirm: () => void;
}) {
  const { t } = useTranslation();
  return (
    <div className="fixed inset-0 z-[var(--z-modal)]">
      <div className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]" />
      <div className={PANEL_STYLE}>
        <div className="flex flex-col flex-1 items-center px-9 pt-[52px] pb-[52px]">
          {/* 성공 컨텐츠 */}
          <div className="flex flex-col items-center w-full gap-[40px]">
            {/* 아이콘 + 타이틀 */}
            <div className="flex flex-col items-center gap-[16px] pt-[36px]">
              <CheckCircleIcon />
              <h2 className="text-[#1e293b] text-[26px] font-semibold tracking-[-0.65px] leading-[1.4] text-center">
                {t('features.register_success_title')}
              </h2>
              <div className="text-[#64748b] text-[18px] font-semibold tracking-[-0.45px] leading-[1.4] text-center">
                <p>{t('features.register_success_subtitle1')}</p>
                <p>{t('features.register_success_subtitle2')}</p>
              </div>
            </div>

            {/* 결과 정보 */}
            <div className="w-full px-[52px] flex flex-col gap-0">
              {/* 첨부 파일 행 */}
              <div className="flex items-center justify-between py-[11px]">
                <span className="text-[#94a3b8] text-[16px] font-medium tracking-[-0.4px]">
                  {t('features.register_success_file_label')}
                </span>
                <div className="flex items-center gap-1 text-[#475569] text-[16px] font-semibold tracking-[-0.4px]">
                  <PaperclipIcon />
                  <span className="max-w-[220px] truncate">{result.fileName}</span>
                </div>
              </div>
              <div className="border-t border-[#e2e8f0]" />
              {/* 메모 행 */}
              <div className="flex items-center justify-between py-[11px]">
                <span className="text-[#94a3b8] text-[16px] font-medium tracking-[-0.4px]">
                  {t('features.register_success_memo_label')}
                </span>
                <span className="text-[#475569] text-[16px] font-semibold tracking-[-0.4px]">
                  {result.memo || '-'}
                </span>
              </div>
              <div className="border-t border-[#e2e8f0]" />
            </div>
          </div>

          {/* 확인 버튼 */}
          <button
            type="button"
            onClick={onConfirm}
            className="h-[48px] w-full rounded-[8px] bg-[#006fff] text-white text-[18px] font-bold tracking-[-0.45px] hover:bg-[#0055cc] transition-colors mt-[40px]"
          >
            {t('features.register_success_confirm')}
          </button>
        </div>
      </div>
    </div>
  );
}

/* ── 삭제 확인 다이얼로그 ── */
function DeleteDialog({
  user,
  isDeleting,
  onConfirm,
  onCancel,
}: {
  user: FeatureRow;
  isDeleting: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const { t } = useTranslation();
  return createPortal(
    <div className="fixed inset-0 z-[var(--z-modal)] flex items-center justify-center">
      {/* 오버레이 */}
      <div className="absolute inset-0 bg-[rgba(20,20,20,0.6)] backdrop-blur-[2px]" onClick={onCancel} />
      {/* 다이얼로그 */}
      <div className="relative z-10 bg-white rounded-[16px] shadow-[0_20px_60px_rgba(0,0,0,0.15)] w-[400px] p-6 flex flex-col gap-5">
        {/* 헤더 */}
        <div className="flex flex-col gap-1.5">
          <h2 className="text-[18px] font-semibold text-[#1e293b] tracking-[-0.45px]">
            {t('features.delete_dialog_title')}
          </h2>
          <p className="text-[14px] text-[#475569] tracking-[-0.35px] leading-[1.6]">
            {t('features.delete_dialog_body')}
          </p>
        </div>

        {/* 대상 정보 */}
        <div className="bg-[#f8fafc] rounded-[10px] px-4 py-3 flex flex-col gap-1">
          <div className="flex gap-2 text-[13px] tracking-[-0.325px]">
            <span className="text-[#94a3b8] font-medium w-[40px] shrink-0">Feature ID</span>
            <span className="text-[#334155] font-medium break-all">{user.featureId}</span>
          </div>
          {user.userDescription && (
            <div className="flex gap-2 text-[13px] tracking-[-0.325px]">
              <span className="text-[#94a3b8] font-medium w-[40px] shrink-0">{t('features.col_memo')}</span>
              <span className="text-[#334155] font-medium">{user.userDescription}</span>
            </div>
          )}
        </div>

        {/* 버튼 */}
        <div className="flex gap-2 justify-end">
          <button
            type="button"
            onClick={onCancel}
            disabled={isDeleting}
            className={[
              'h-[40px] px-5 rounded-[8px] border border-[#cbd5e1]',
              'text-[14px] font-medium text-[#475569] tracking-[-0.35px]',
              'hover:bg-[#f1f5f9] transition-colors disabled:opacity-50',
            ].join(' ')}
          >
            {t('common.cancel')}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={isDeleting}
            className={[
              'h-[40px] px-5 rounded-[8px]',
              'bg-[var(--color-entry)] hover:bg-[#c02020]',
              'text-[14px] font-semibold text-white tracking-[-0.35px]',
              'transition-colors disabled:opacity-50 flex items-center gap-1.5',
            ].join(' ')}
          >
            {isDeleting ? t('features.deleting') : t('common.delete')}
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
}

/* ── 메인 컴포넌트 ── */
export default function FeaturesPage() {
  const { t }               = useTranslation();
  const queryClient         = useQueryClient();
  const { selectedId, selectedProject } = useProjectContext();
  const isExternal = selectedProject?.projectType === 'EXTERNAL';
  const location            = useLocation();
  const navigate            = useNavigate();

  const [searchInput, setSearchInput] = useState('');
  /* 인증방식 탭 (기본: 모든 방식) — 얼굴/손바닥 필터는 백엔드 modality 지원 시 연결 */
  const [authTab, setAuthTab] = useState<'all' | 'face' | 'palm'>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [pageNum,     setPageNum]     = useState(1);
  const [pageSize,    setPageSize]    = useState(10);

  /* 삭제 다이얼로그 */
  const [deleteTarget, setDeleteTarget] = useState<FeatureRow | null>(null);

  /* 등록 패널 */
  const [showRegisterPanel, setShowRegisterPanel] = useState(false);
  const [registerResult,    setRegisterResult]    = useState<RegisterResult | null>(null);

  /* 알림 */
  const [alert, setAlert] = useState<{ message: string; variant: 'success' | 'error' } | null>(null);

  /* 검색 실행 (Enter 또는 버튼) */
  const applySearch = () => {
    setSearchQuery(searchInput.trim());
    setPageNum(1);
  };

  /* API 호출 */
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['features', selectedId, pageNum, pageSize, searchQuery, authTab],
    queryFn: async () => {
      const params: Parameters<typeof getFeatures>[0] = {
        page:     pageNum,
        pageSize,
        isDeleted: false,
      };
      if (authTab !== 'all') params.featureType = authTab === 'palm' ? 'PALM' : 'FACE';
      if (searchQuery) params.keyword = searchQuery;
      return getFeatures(params);
    },
    enabled: !!localStorage.getItem('access_token') && !!selectedProject,
    staleTime: 0,
  });

  /* 사이드바 메뉴 클릭 등 페이지 재진입 시 강제 갱신 */
  useEffect(() => {
    refetch();
  }, [location.key]); // eslint-disable-line react-hooks/exhaustive-deps

  /* 삭제 mutation */
  const { mutate: execDelete, isPending: isDeleting } = useMutation({
    mutationFn: (row: FeatureRow) => deleteFeature(row.featureType, row.featureSeq),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['features'] });
      setDeleteTarget(null);
      setAlert({ message: t('features.delete_success'), variant: 'success' });
    },
    onError: (err: unknown) => {
      const status = (err as { response?: { status?: number } })?.response?.status;
      const msg = status
        ? `${t('features.delete_error')} (${status})`
        : t('features.delete_error');
      setAlert({ message: msg, variant: 'error' });
    },
  });

  const users         = data?.content ?? [];
  const totalPages    = data?.page.totalPages ?? 1;
  const totalElements = data?.page.totalElements ?? 0;
  const totalCount    = data?.page.totalCount;

  return (
    <DashboardLayout>
      {/* 알림 */}
      {alert && (
        <Alert
          message={alert.message}
          variant={alert.variant}
          onClose={() => setAlert(null)}
        />
      )}

      {/* 수동 등록 패널 */}
      {showRegisterPanel && !registerResult && (
        <RegisterPanel
          onClose={() => setShowRegisterPanel(false)}
          onSuccess={(result) => {
            queryClient.invalidateQueries({ queryKey: ['features'] });
            setRegisterResult(result);
          }}
        />
      )}

      {/* 수동 등록 완료 패널 */}
      {registerResult && (
        <RegisterSuccessPanel
          result={registerResult}
          onConfirm={() => {
            setRegisterResult(null);
            setShowRegisterPanel(false);
          }}
        />
      )}

      {/* 삭제 확인 다이얼로그 */}
      {deleteTarget && (
        <DeleteDialog
          user={deleteTarget}
          isDeleting={isDeleting}
          onConfirm={() => execDelete(deleteTarget)}
          onCancel={() => setDeleteTarget(null)}
        />
      )}

      <div className="flex flex-col gap-5">
        {/* 페이지 타이틀 */}
         <h1 className="text-[26px] font-semibold text-[var(--color-neutral-800)] tracking-[-0.65px] leading-[var(--leading-normal)]">
          {t('features.title')}
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
              onClick={() => { setAuthTab(tab.id); setPageNum(1); }}
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

        {/* 필터 바 */}
        <div className="flex items-center justify-between mt-[0px]">

          {/* 검색 입력 + 버튼 */}
          <div className="flex items-center gap-2">
            <input
              type="text"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') applySearch(); }}
              placeholder={t('features.search_placeholder')}
              className={[
                'h-[44px] w-[300px] px-3',
                'border border-[#CBD5E1] rounded-[8px] bg-white',
                'text-[14px] font-medium text-[var(--color-neutral-800)] tracking-[-0.35px]',
                'placeholder:text-[var(--color-neutral-400)]',
                'focus:outline-none focus:border-[var(--color-link-blue)] transition-colors',
              ].join(' ')}
            />
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
              onClick={() => { setSearchInput(''); setSearchQuery(''); setPageNum(1); }}
              className="h-[44px] w-[44px] flex items-center justify-center rounded-[8px] border border-[#cbd5e1] bg-white hover:bg-[#f1f5f9] transition-colors"
            >
              <RefreshIcon size={20} />
            </button>
          </div>

          {/* 수동 등록 버튼 — 얼굴만 가능. 외부 연동형/손바닥 탭에서는 숨김 */}
          {!isExternal && authTab !== 'palm' && (
            <button
              type="button"
              onClick={() => setShowRegisterPanel(true)}
              className={[
                'flex items-center gap-1 h-[40px] px-4 rounded-[8px]',
                'bg-[var(--color-link-blue)] hover:bg-[var(--color-link-blue-hover)]',
                'text-[14px] font-semibold text-white tracking-[-0.35px] transition-colors',
              ].join(' ')}
            >
              {t('features.register_btn')}
            </button>
          )}
        </div>

        {/* 테이블 */}
        <div className="mt-[0px] w-full overflow-auto max-h-[calc(100vh-340px)]">
          <table className="w-full border-separate border-spacing-0">
            <thead>
              <tr>
                {/* 체크박스 컬럼 — 미사용으로 히든 */}
                <th className="hidden" />
                <th className={TH}>{t('logs.serial_no')}</th>
                <th className={TH}>{t('projects.col_auth_method')}</th>
                <th className={TH}>{t('features.col_memo')}</th>
                <th className={TH}>Feature ID</th>
                <th className={TH}>
                  <div className="flex items-center justify-center gap-1">
                    <span>{t('features.col_date')}</span>
                    <SortIcon />
                  </div>
                </th>
                <th className={TH}>{t('features.col_action')}</th>
                {!isExternal && <th className={TH}>{t('features.col_delete')}</th>}
              </tr>
            </thead>
            <tbody>
              {isLoading && (
                <tr>
                  <td colSpan={isExternal ? 6 : 7} className="px-4 py-8 text-center text-[14px] text-[var(--color-neutral-400)]">
                    {t('common.loading')}
                  </td>
                </tr>
              )}
              {isError && (
                <tr>
                  <td colSpan={isExternal ? 6 : 7} className="px-4 py-8 text-center text-[14px] text-[var(--color-entry)]">
                    {t('common.load_error')} ({(error as Error)?.message ?? t('common.unknown_error')})
                  </td>
                </tr>
              )}
              {!isLoading && !isError && users.length === 0 && (
                <tr>
                  <td colSpan={isExternal ? 6 : 7} className="px-4 py-8 text-center text-[14px] text-[var(--color-neutral-400)]">
                    {t('common.no_data')}
                  </td>
                </tr>
              )}
              {users.map((user) => (
                <tr
                  key={user.featureSeq}
                  className="[&>td]:border-b [&>td]:border-[#e2e8f0] hover:bg-[#F8FAFC] transition-colors"
                >
                  {/* 체크박스 셀 — 히든 */}
                  <td className="hidden" />
                  {/* 일련번호 */}
                  <td className={`${TD} text-center text-[14px] font-medium text-[#475569] tracking-[-0.35px]`}>
                    {user.featureSeq}
                  </td>
                  {/* 인증 방식 */}
                  <td className={`${TD} text-center`}>
                    <AuthMethodBadge palm={user.featureType === 'PALM'} label={user.featureType === 'PALM' ? t('auth_type.palm_short') : t('auth_type.face_short')} />
                  </td>
                  <td className={TD}>
                    <span className="text-[14px] font-medium text-[#475569] tracking-[-0.35px] leading-[1.4]">
                      {user.userDescription ?? '-'}
                    </span>
                  </td>
                  <td className={`${TD} text-center`}>
                    <span className="text-[14px] font-medium text-[#334155] tracking-[-0.35px] leading-[1.4]">
                      {user.featureId}
                    </span>
                  </td>
                  <td className={`${TD} text-center`}>
                    <span className="text-[14px] font-medium text-[#475569] tracking-[-0.35px] leading-[1.4]">
                      {formatDate(user.createdAt)}
                    </span>
                  </td>
                  {/* 로그 조회 */}
                  <td className={`${TD} text-center`}>
                    <div className="flex justify-center">
                    <button
                      type="button"
                      onClick={() => navigate(`/dashboard/logs?fid=${encodeURIComponent(user.featureId)}`)}
                      className={[
                        'flex items-center gap-1.5 h-[32px] px-3 rounded-[6px]',
                        'border border-[#93c5fd] text-[#006fff]',
                        'text-[13px] font-medium tracking-[-0.325px]',
                        'hover:bg-[#eff6ff] transition-colors',
                      ].join(' ')}
                    >
                      <LogIcon />
                      {t('features.action_view_log')}
                    </button>
                    </div>
                  </td>
                  {/* 삭제 — 외부 연동형 프로젝트에서는 숨김 */}
                  {!isExternal && (
                    <td className={`${TD} text-center`}>
                      <div className="flex justify-center">
                      <button
                        type="button"
                        onClick={() => setDeleteTarget(user)}
                        className={[
                          'flex items-center gap-1.5 h-[32px] px-3 rounded-[6px]',
                          'border border-[#fca5a5] text-[var(--color-entry)]',
                          'text-[13px] font-medium tracking-[-0.325px]',
                          'hover:bg-[#fff7f6] transition-colors',
                        ].join(' ')}
                      >
                        <TrashIcon />
                        {t('common.delete')}
                      </button>
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* 페이지네이션 */}
        <Pagination
          pageNum={pageNum}
          pageSize={pageSize}
          totalPages={totalPages}
          totalElements={totalElements}
          totalRecords={totalCount}
          currentCount={users.length}
          onPageChange={setPageNum}
          onPageSizeChange={(size) => { setPageSize(size); setPageNum(1); }}
        />

      </div>
    </DashboardLayout>
  );
}
