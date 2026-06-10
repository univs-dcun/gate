/**
 * WelcomeLayout — 프로젝트 없는 초기 화면 전용 레이아웃
 *
 * Figma node 197-6539 기반
 * DashboardLayout과 달리 탑바/배너 없음 + 전용 사이드바
 */

import { useState, useRef, useEffect } from 'react';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Logo } from '@/components/ui';
import { changeLanguage } from '@/i18n';
import { getCompanyInfo } from '@/services/account';

/* ── 인라인 SVG 아이콘 ── */
const ChevronsLeftIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="11 17 6 12 11 7" /><polyline points="18 17 13 12 18 7" />
  </svg>
);

const ChevronsRightIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="13 17 18 12 13 7" /><polyline points="6 17 11 12 6 7" />
  </svg>
);

const FolderOpenIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <path d="M20 6h-8l-2-2H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10z" />
  </svg>
);

const LogOutIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
    <polyline points="16 17 21 12 16 7" />
    <line x1="21" y1="12" x2="9" y2="12" />
  </svg>
);

const GlobeIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10" />
    <line x1="2" y1="12" x2="22" y2="12" />
    <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
  </svg>
);

const ChevronUpIcon = ({ open }: { open: boolean }) => (
  <svg
    width="14" height="14" viewBox="0 0 24 24" fill="none"
    stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
    style={{ transform: open ? 'rotate(0deg)' : 'rotate(180deg)', transition: 'transform 0.2s' }}
  >
    <polyline points="18 15 12 9 6 15" />
  </svg>
);

/* ── 언어 옵션 ── */
const LANG_OPTIONS = [
  { code: 'ko', label: '한국어', sub: 'Korean' },
  { code: 'en', label: 'English', sub: '영어' },
] as const;

/* ── 이메일에서 이니셜 추출 ── */
function getInitials(email: string): string {
  const local = email.split('@')[0] ?? '';
  return local.slice(0, 2).toUpperCase();
}

/* ── 스타일 상수 ── */
const SIDEBAR_SHELL = [
  'flex-shrink-0 h-screen overflow-y-auto z-[var(--z-sticky)]',
  'border-r border-[var(--color-border-default)]',
].join(' ');

const ICON_BTN = [
  'flex h-8 w-8 items-center justify-center rounded-[var(--radius-md)] transition-colors',
  'text-[var(--color-text-secondary)] hover:bg-[var(--sidebar-nav-hover-bg)]',
].join(' ');

interface WelcomeLayoutProps {
  children: ReactNode;
}

export default function WelcomeLayout({ children }: WelcomeLayoutProps) {
  const { t, i18n } = useTranslation();
  const navigate    = useNavigate();
  const queryClient = useQueryClient();
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [langOpen, setLangOpen]       = useState(false);
  const langRef                       = useRef<HTMLDivElement>(null);

  const currentLang = i18n.language as 'ko' | 'en';
  const currentLangOption = LANG_OPTIONS.find(l => l.code === currentLang) ?? LANG_OPTIONS[0];

  const email    = localStorage.getItem('user_email') ?? '';
  const initials = getInitials(email);

  const { data: companyData } = useQuery({
    queryKey: ['company'],
    queryFn:  () => getCompanyInfo().then(r => r.data.data),
    staleTime: 5 * 60 * 1000,
  });
  const companyName = companyData?.companyName || null;

  /* 외부 클릭 시 언어 드롭다운 닫기 */
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (langRef.current && !langRef.current.contains(e.target as Node)) {
        setLangOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  function handleLogout() {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    localStorage.removeItem('user_email');
    localStorage.removeItem('remembered_email');
    queryClient.clear();
    navigate('/login');
  }

  /* ── 접힌 상태 (52px) ── */
  if (isCollapsed) {
    return (
      <div className="flex h-screen overflow-hidden bg-[var(--color-bg-page)] min-w-[1320px] max-w-[1920px] mx-auto w-full">
        <aside
          className={[SIDEBAR_SHELL, 'flex flex-col items-center py-3 gap-2'].join(' ')}
          style={{ width: '52px', background: 'var(--color-bg-surface)' }}
        >
          <button onClick={() => setIsCollapsed(false)} className={ICON_BTN} title="사이드바 펼치기">
            <ChevronsRightIcon />
          </button>
          <div className="w-8 h-px bg-[#CBD5E1] my-1" />
          <button
            onClick={() => navigate('/projects')}
            className={[ICON_BTN, 'text-[#1E293B]'].join(' ')}
            title={t('projects.list_menu')}
          >
            <FolderOpenIcon />
          </button>
          <div className="mt-auto" />
          <button
            onClick={() => changeLanguage(currentLang === 'ko' ? 'en' : 'ko')}
            title={currentLang === 'ko' ? 'English' : '한국어'}
            className={ICON_BTN}
          >
            <GlobeIcon />
          </button>
          {/* 프로필 위 구분선만 */}
          <div className="w-8 h-px bg-[#CBD5E1] my-1" />
          <button
            onClick={handleLogout}
            title={t('auth.logout')}
            className={[ICON_BTN, 'hover:bg-[var(--color-entry-bg)] hover:text-[var(--color-entry)]'].join(' ')}
          >
            <LogOutIcon />
          </button>
        </aside>
        <main className="flex-1 overflow-y-auto">
          {children}
        </main>
      </div>
    );
  }

  /* ── 펼친 상태 ── */
  return (
    <div className="flex h-screen overflow-hidden bg-[var(--color-bg-page)] min-w-[1320px] max-w-[1920px] mx-auto w-full">

      {/* ── 사이드바 ── */}
      <aside
        className={[SIDEBAR_SHELL, 'flex flex-col'].join(' ')}
        style={{ width: 'var(--sidebar-width)', background: 'var(--color-bg-surface)' }}
      >

        {/* 헤더: 로고 + 접기 버튼 */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-[var(--color-border-default)] flex-shrink-0">
          <button type="button" onClick={() => navigate('/projects')} className="flex items-center">
            <Logo height={30} />
          </button>
          <button
            onClick={() => setIsCollapsed(true)}
            className={ICON_BTN}
            title={t('common.collapse_sidebar')}
          >
            <ChevronsLeftIcon />
          </button>
        </div>

        <div className="py-4 mx-3" />

        {/* 네비게이션 */}
        <nav className="px-[11px] flex-shrink-0">
          <div className="flex flex-col px-3 py-2.5 rounded-[8px] gap-1.5">
            <span className="text-[14px] font-semibold text-[#1E293B] tracking-[-0.35px] leading-[1.4]">
              {t('projects.sidebar_status')}
            </span>
            <span className="text-[12px] font-normal text-[#64748B] tracking-[-0.3px] leading-[1.4]">
              {t('projects.sidebar_select_hint')}
            </span>
          </div>
        </nav>

        {/* 하단: 언어 변경 + 구분선 + 사용자 정보 */}
        <div className="mt-auto flex-shrink-0">

          {/* 언어 선택 */}
          <div ref={langRef} className="px-3 pt-3 pb-1 relative">
            <button
              onClick={() => setLangOpen(o => !o)}
              className={[
                'flex items-center justify-between w-full px-2 py-2 rounded-[var(--radius-md)]',
                'text-sm font-medium transition-colors',
                'hover:bg-[var(--sidebar-nav-hover-bg)]',
              ].join(' ')}
            >
              <div className="flex items-center gap-2 text-[var(--color-text-secondary)]">
                <GlobeIcon />
                <span className="text-[12px] tracking-[-0.3px]">
                  {currentLangOption.label} / {currentLangOption.sub}
                </span>
              </div>
              <ChevronUpIcon open={langOpen} />
            </button>

            {/* 드롭다운 */}
            {langOpen && (
              <div className="absolute left-3 right-3 bottom-full mb-1 bg-white border border-[var(--color-border-default)] rounded-[var(--radius-md)] shadow-md overflow-hidden z-10">
                {LANG_OPTIONS.map((opt) => (
                  <button
                    key={opt.code}
                    onClick={() => { changeLanguage(opt.code); setLangOpen(false); }}
                    className={[
                      'flex items-center gap-2 w-full px-3 py-2 text-[13px] tracking-[-0.325px] transition-colors',
                      currentLang === opt.code
                        ? 'bg-[var(--color-blue-10)] text-[var(--color-link-blue)] font-semibold'
                        : 'text-[var(--color-text-primary)] hover:bg-[var(--color-neutral-50)]',
                    ].join(' ')}
                  >
                    <span>{opt.label}</span>
                    <span className="text-[var(--color-text-secondary)]">/ {opt.sub}</span>
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* 프로필 위 구분선만 */}
          <div className="h-px bg-[#CBD5E1] mx-3" />

          {/* 사용자 정보 + 로그아웃 */}
          <div className="px-3 py-6">
            <div className="flex items-center justify-between gap-2 px-2">
              <div className="flex items-center gap-2 min-w-0">
                <div className={[
                  'flex-shrink-0 w-7 h-7 rounded-full bg-[var(--color-link-blue)]',
                  'flex items-center justify-center text-white text-xs font-semibold',
                ].join(' ')}>
                  {initials}
                </div>
                <div className="flex flex-col min-w-0 gap-0.5">
                  {companyName && (
                    <span className="text-[13px] font-semibold text-[#1E293B] tracking-[-0.325px] truncate leading-[1.3]">
                      {companyName}
                    </span>
                  )}
                  <span className="text-xs text-[var(--color-text-secondary)] truncate leading-[1.3]">
                    {email || '—'}
                  </span>
                </div>
              </div>
              <button
                type="button"
                onClick={handleLogout}
                title={t('auth.logout')}
                className={[
                  'flex-shrink-0 flex h-7 w-7 items-center justify-center rounded-[var(--radius-md)]',
                  'text-[var(--color-text-secondary)] transition-colors',
                  'hover:bg-[var(--color-entry-bg)] hover:text-[var(--color-entry)]',
                ].join(' ')}
              >
                <LogOutIcon />
              </button>
            </div>
          </div>

        </div>
      </aside>
      

      {/* ── 메인 콘텐츠 ── */}
      <main
        className="flex-1 overflow-y-auto"
      >
        {children}
      </main>
    </div>
  );
}
