import { useState, useRef, useEffect } from 'react';
import type { ReactElement } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Logo, ProjectCombobox } from '@/components/ui';
import { useProjectContext } from '@/contexts/ProjectContext';
import { changeLanguage } from '@/i18n';
import { getCompanyInfo } from '@/services/account';

/* ── 라우트 맵 ── */
const ROUTE_MAP: Record<string, string> = {
  dashboard: '/dashboard',
  features:  '/dashboard/features',
  logs:      '/dashboard/logs',
  support:   '/dashboard/support',
  settings:  '/dashboard/settings',
};

/* ── 인라인 SVG 아이콘 ── */
/* outline(기본/호버) vs filled(선택) 아이콘 */
const HomeIcon = ({ active }: { active?: boolean }) => active ? (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
    <path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z" />
  </svg>
) : (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z" /><polyline points="9 22 9 12 15 12 15 22" />
  </svg>
);
const SlidersIcon = ({ active }: { active?: boolean }) => active ? (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
    <path d="M3 18h6v-2H3v2zM3 6v2h18V6H3zm0 7h12v-2H3v2z" />
  </svg>
) : (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <line x1="4" y1="21" x2="4" y2="14" /><line x1="4" y1="10" x2="4" y2="3" />
    <line x1="12" y1="21" x2="12" y2="12" /><line x1="12" y1="8" x2="12" y2="3" />
    <line x1="20" y1="21" x2="20" y2="16" /><line x1="20" y1="12" x2="20" y2="3" />
    <line x1="1" y1="14" x2="7" y2="14" /><line x1="9" y1="8" x2="15" y2="8" /><line x1="17" y1="16" x2="23" y2="16" />
  </svg>
);
const ListIcon = ({ active }: { active?: boolean }) => active ? (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
    <path d="M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z" />
  </svg>
) : (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <line x1="8" y1="6" x2="21" y2="6" /><line x1="8" y1="12" x2="21" y2="12" />
    <line x1="8" y1="18" x2="21" y2="18" /><line x1="3" y1="6" x2="3.01" y2="6" />
    <line x1="3" y1="12" x2="3.01" y2="12" /><line x1="3" y1="18" x2="3.01" y2="18" />
  </svg>
);
const HelpIcon = ({ active }: { active?: boolean }) => active ? (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17h-2v-2h2v2zm2.07-7.75l-.9.92C13.45 12.9 13 13.5 13 15h-2v-.5c0-1.1.45-2.1 1.17-2.83l1.24-1.26c.37-.36.59-.86.59-1.41 0-1.1-.9-2-2-2s-2 .9-2 2H8c0-2.21 1.79-4 4-4s4 1.79 4 4c0 .88-.36 1.68-.93 2.25z" />
  </svg>
) : (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10" />
    <path d="M9.09 9a3 3 0 015.83 1c0 2-3 3-3 3" /><line x1="12" y1="17" x2="12.01" y2="17" />
  </svg>
);
const SettingsIcon = ({ active }: { active?: boolean }) => active ? (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
    <path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z" />
  </svg>
) : (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="3" />
    <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z" />
  </svg>
);
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
const LogoutIcon = () => (
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

interface NavItem {
  id:      string;
  i18nKey: string;
  icon: (props: { active?: boolean }) => ReactElement;
}

const NAV_ITEMS: NavItem[] = [
  { id: 'dashboard', i18nKey: 'nav.dashboard',   icon: HomeIcon },
  { id: 'features',  i18nKey: 'nav.features',     icon: SlidersIcon },
  { id: 'logs',      i18nKey: 'nav.logs',         icon: ListIcon },
  { id: 'support',   i18nKey: 'nav.support',      icon: HelpIcon },
  { id: 'settings',  i18nKey: 'nav.settings',     icon: SettingsIcon },
];

interface NavGroup {
  groupKey: string;
  ids: string[];
}

const NAV_GROUPS: NavGroup[] = [
  { groupKey: 'nav.group_main',    ids: ['dashboard', 'features', 'logs'] },
  { groupKey: 'nav.group_support', ids: ['support', 'settings'] },
];

/* ── 스타일 상수 ── */
const SIDEBAR_SHELL = [
  'flex-shrink-0 h-screen overflow-y-auto',
  'bg-[var(--sidebar-bg)] border-r border-[var(--color-border-default)] z-[var(--z-sticky)]',
].join(' ');

const ICON_BTN_BASE = 'flex h-8 w-8 items-center justify-center rounded-[var(--radius-md)] transition-colors';
const ICON_BTN_ACTIVE   = 'bg-[var(--sidebar-nav-active-bg)] text-[var(--sidebar-nav-active-text)]';
const ICON_BTN_INACTIVE = 'text-[var(--sidebar-nav-text)] hover:bg-[var(--sidebar-nav-hover-bg)]';

const NAV_BTN_BASE = [
  'flex items-center w-full pl-5 pr-4 py-3 text-left',
  'text-[14px] font-medium tracking-[-0.28px] transition-all duration-[var(--transition-fast)]',
].join(' ');
const NAV_BTN_ACTIVE   = 'gap-[10px] bg-white rounded-[8px] shadow-[0px_1px_1.5px_0px_rgba(0,0,0,0.06)] text-[#006fff] pr-6';
const NAV_BTN_INACTIVE = 'gap-3 text-[#475569] hover:bg-white hover:rounded-[12px] hover:text-[#334155]';

/**
 * Figma 메인화면 기반 — 화이트 텍스트 네비게이션 사이드바
 * react-router useNavigate/useLocation 기반으로 현재 경로를 자동으로 활성 상태로 표시
 */
function Sidebar() {
  const navigate = useNavigate();
  const location = useLocation();
  const { t, i18n } = useTranslation();
  const queryClient = useQueryClient();
  const [isCollapsed, setIsCollapsed] = useState(
    () => window.innerWidth < 1280 || localStorage.getItem('sidebar_collapsed') === 'true',
  );
  const [langOpen, setLangOpen] = useState(false);

  /* localStorage 영속화 */
  useEffect(() => {
    localStorage.setItem('sidebar_collapsed', String(isCollapsed));
  }, [isCollapsed]);

  /* 1280px 기준 자동 접힘/펼침 */
  useEffect(() => {
    const handleResize = () => {
      if (window.innerWidth < 1280) setIsCollapsed(true);
      else setIsCollapsed(false);
    };
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);
  const langRef                       = useRef<HTMLDivElement>(null);
  const currentLang = i18n.language as 'ko' | 'en';
  const currentLangOption = LANG_OPTIONS.find(l => l.code === currentLang) ?? LANG_OPTIONS[0];

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
  const { projects, selectedId: selectedProjectId, setSelectedId: setSelectedProjectId } = useProjectContext();
  const userEmail = localStorage.getItem('user_email') ?? '';
  const initials  = userEmail.slice(0, 2).toUpperCase() || 'ME';

  const { data: companyData } = useQuery({
    queryKey: ['company'],
    queryFn:  () => getCompanyInfo().then(r => r.data.data),
    staleTime: 5 * 60 * 1000,
  });
  const companyName = companyData?.companyName || null;

  const handleLogout = () => {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    localStorage.removeItem('user_email');
    localStorage.removeItem('remembered_email');
    localStorage.removeItem('selected_project_id');
    localStorage.removeItem('selected_api_key');
    queryClient.clear();
    navigate('/login');
  };

  const activeItem =
    Object.entries(ROUTE_MAP).find(([, path]) => location.pathname === path)?.[0] ?? 'dashboard';

  const handleNavClick = (id: string) => {
    if (id === 'features') {
      queryClient.invalidateQueries({ queryKey: ['users'] });
    }
    navigate(ROUTE_MAP[id] ?? '/dashboard');
  };

  /* ── 접힌 상태 ── */
  if (isCollapsed) {
    return (
      <aside
        className={[SIDEBAR_SHELL, 'flex flex-col items-center py-3 gap-2'].join(' ')}
        style={{ width: '56px', transition: 'width 0.2s ease' }}
      >
        <button
          onClick={() => setIsCollapsed(false)}
          className={[ICON_BTN_BASE, 'mt-1 text-[var(--color-text-secondary)] hover:bg-[var(--sidebar-nav-hover-bg)]'].join(' ')}
          title="사이드바 펼치기"
        >
          <ChevronsRightIcon />
        </button>
        <div className="w-8 h-px bg-[var(--color-border-default)] my-1" />
        {NAV_ITEMS.map((item) => {
          const Icon = item.icon;
          const isActive = item.id === activeItem;
          return (
            <div key={item.id} className="relative group">
              <button
                onClick={() => handleNavClick(item.id)}
                className={[ICON_BTN_BASE, isActive ? ICON_BTN_ACTIVE : ICON_BTN_INACTIVE].join(' ')}
              >
                <Icon active={isActive} />
              </button>
              <div className={[
                'absolute left-full top-1/2 -translate-y-1/2 ml-2.5',
                'px-2 py-1 rounded-[var(--radius-sm)] bg-[#1E293B] text-white text-[11px] font-medium whitespace-nowrap',
                'pointer-events-none z-[200]',
                'opacity-0 group-hover:opacity-100 transition-opacity duration-150',
              ].join(' ')}>
                {t(item.i18nKey)}
              </div>
            </div>
          );
        })}
        {/* 언어 + 로그아웃 */}
        <div className="mt-auto" />
        <button
          onClick={() => changeLanguage(currentLang === 'ko' ? 'en' : 'ko')}
          title={currentLang === 'ko' ? 'English' : '한국어'}
          className={[ICON_BTN_BASE, 'text-[var(--color-text-secondary)] hover:bg-[var(--sidebar-nav-hover-bg)]'].join(' ')}
        >
          <GlobeIcon />
        </button>
        {/* 프로필 위 구분선만 */}
        <div className="w-8 h-px bg-[#CBD5E1] my-1" />
        <button
          onClick={handleLogout}
          title="로그아웃"
          className={[ICON_BTN_BASE, 'text-[var(--color-text-secondary)] hover:bg-[var(--color-entry-bg)] hover:text-[var(--color-entry)]'].join(' ')}
        >
          <LogoutIcon />
        </button>
      </aside>
    );
  }

  /* ── 펼친 상태 ── */
  return (
    <aside
      className={[SIDEBAR_SHELL, 'flex flex-col'].join(' ')}
      style={{ width: 'var(--sidebar-width)', transition: 'width 0.2s ease' }}
    >
      {/* 헤더: 로고 + 접기 */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-[var(--color-border-default)] flex-shrink-0">
        <button type="button" onClick={() => navigate('/projects')} className="flex items-center">
          <Logo height={30} />
        </button>
        <button
          onClick={() => setIsCollapsed(true)}
          className={[ICON_BTN_BASE, 'text-[var(--color-text-secondary)] hover:bg-[var(--sidebar-nav-hover-bg)]'].join(' ')}
          title="사이드바 접기"
        >
          <ChevronsLeftIcon />
        </button>
      </div>

      {/* 프로젝트 선택 — Figma node 285-2677, 84-1712 */}
      <div className="mx-[12px] mt-[16px] mb-[12px] px-0 py-0 border-b border-[var(--color-border-default)] flex-shrink-0">
        <ProjectCombobox
          projects={projects}
          selectedId={selectedProjectId}
          onSelect={setSelectedProjectId}
          onViewAll={() => navigate('/projects')}
        />
      </div>

      {/* 네비게이션 */}
      <nav className="flex-1 py-3 flex flex-col gap-4 overflow-y-auto">
        {NAV_GROUPS.map((group) => (
          <div key={group.groupKey}>
            {/* 그룹 타이틀 */}
            <div className="flex items-center gap-[10px] pb-1 pl-6">
              <span className="text-[11px] font-semibold text-[var(--color-text-tertiary)] tracking-[0.08em] uppercase leading-[1.4]">
                {t(group.groupKey)}
              </span>
            </div>
            {/* 그룹 아이템 */}
            <div className="flex flex-col gap-0.5 px-2 m-3">
              {group.ids.map((id) => {
                const item = NAV_ITEMS.find((n) => n.id === id)!;
                const Icon = item.icon;
                const isActive = item.id === activeItem;
                return (
                  <button
                    key={item.id}
                    onClick={() => handleNavClick(item.id)}
                    className={[NAV_BTN_BASE, isActive ? NAV_BTN_ACTIVE : NAV_BTN_INACTIVE].join(' ')}
                  >
                    <Icon active={isActive} />
                    <span>{t(item.i18nKey)}</span>
                  </button>
                );
              })}
            </div>
            <div className="w-full h-px my-1" />
          </div>
        ))}
      </nav>

      {/* 하단: 언어 변경 + 구분선 + 사용자 정보 + 로그아웃 */}
      <div className="flex-shrink-0">
        {/* 언어 선택 */}
        <div ref={langRef} className="px-3 pt-3 pb-1 relative">
          <button
            onClick={() => setLangOpen(o => !o)}
            className={[
              'flex items-center justify-between w-full px-2 py-2 rounded-[var(--radius-md)]',
              'text-sm text-[var(--color-text-primary)] font-medium transition-colors',
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
                    'flex items-center gap-2 w-full px-3 py-2 text-[12px] tracking-[-0.3px] transition-colors',
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

        {/* 구분선 — 프로필 위에만 */}
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
                  {userEmail || '—'}
                </span>
              </div>
            </div>
            <button
              onClick={handleLogout}
              title="로그아웃"
              className={[
                'flex-shrink-0 flex h-7 w-7 items-center justify-center rounded-[var(--radius-md)]',
                'text-[var(--color-text-secondary)] transition-colors',
                'hover:bg-[var(--color-entry-bg)] hover:text-[var(--color-entry)]',
              ].join(' ')}
            >
              <LogoutIcon />
            </button>
          </div>
        </div>


      </div>
    </aside>
  );
}

export default Sidebar;

/* ── 공유 언어 선택 드롭다운 ── */
export function LangSelector() {
  const { i18n } = useTranslation();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const currentLang = i18n.language as 'ko' | 'en';
  const current = LANG_OPTIONS.find(l => l.code === currentLang) ?? LANG_OPTIONS[0];

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  return (
    <div ref={ref} className="relative self-start">
      <button
        onClick={() => setOpen(o => !o)}
        className={[
          'flex items-center gap-2 px-3 py-2 rounded-[8px]',
          'bg-white border border-[#cbd5e1] text-[#475569]',
          'hover:border-[#006fff] transition-colors',
        ].join(' ')}
      >
        <GlobeIcon />
        <span className="text-[13px] font-medium tracking-[-0.325px]">
          {current.label} / {current.sub}
        </span>
        <ChevronUpIcon open={open} />
      </button>

      {open && (
        <div className="absolute left-0 top-full mt-1 bg-white border border-[#e2e8f0] rounded-[8px] shadow-md overflow-hidden z-10 min-w-full">
          {LANG_OPTIONS.map((opt) => (
            <button
              key={opt.code}
              onClick={() => { changeLanguage(opt.code); setOpen(false); }}
              className={[
                'flex items-center gap-2 w-full px-3 py-2 text-[13px] tracking-[-0.325px] transition-colors',
                currentLang === opt.code
                  ? 'bg-[var(--color-blue-10)] text-[var(--color-link-blue)] font-semibold'
                  : 'text-[#475569] hover:bg-[#f8fafc]',
              ].join(' ')}
            >
              <span>{opt.label}</span>
              <span className="text-[#94a3b8]">/ {opt.sub}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
