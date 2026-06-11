import { useState, useRef, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Logo, ConsentBadge, LanguageSelect } from '@/components/ui';
import { useProjectContext } from '@/contexts/ProjectContext';
import { getCompanyInfo } from '@/services/account';

/**
 * TopNav (GNB) — 상단 가로 네비게이션 바
 *
 * Figma node 1735:8333 (MaxGNB) 기반. 기존 Sidebar와 병행하는 레이아웃 옵션.
 *  [로고 + 프로젝트 선택] ─── [메뉴] ─── [동의 뱃지 + 언어 + 프로필]
 *
 * 높이 52px, 흰 배경 + 하단 보더. 비즈니스 로직(프로젝트/언어/로그아웃)은 Sidebar와 동일하게 재사용.
 */

/* ── 라우트 맵 (Sidebar와 동일) ── */
const ROUTE_MAP: Record<string, string> = {
  dashboard: '/dashboard',
  features:  '/dashboard/features',
  logs:      '/dashboard/logs',
  support:   '/dashboard/support',
  settings:  '/dashboard/settings',
};

const NAV_ITEMS: { id: string; i18nKey: string }[] = [
  { id: 'dashboard', i18nKey: 'nav.dashboard' },
  { id: 'features',  i18nKey: 'nav.features' },
  { id: 'logs',      i18nKey: 'nav.logs' },
  { id: 'support',   i18nKey: 'nav.support' },
  { id: 'settings',  i18nKey: 'nav.settings' },
];

/* ── 아이콘 ── */
function ChevronIcon({ open }: { open: boolean }) {
  return (
    <svg
      width="16" height="16" viewBox="0 0 20 20" fill="none"
      className={['transition-transform duration-200 flex-shrink-0', open ? 'rotate-180' : ''].join(' ')}
      aria-hidden="true"
    >
      <path d="M5 7.5L10 12.5L15 7.5" stroke="#94A3B8" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
const LogoutIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
    <polyline points="16 17 21 12 16 7" /><line x1="21" y1="12" x2="9" y2="12" />
  </svg>
);

/* ── 메뉴 아이템 스타일 (Figma Component17) ── */
const NAV_ITEM_BASE = 'flex items-center justify-center px-3 py-1.5 rounded-[12px] whitespace-nowrap transition-colors';
const NAV_ITEM_ACTIVE = 'font-bold text-[var(--color-neutral-800)]';
const NAV_ITEM_INACTIVE = 'font-normal text-[var(--color-neutral-500)] hover:text-[var(--color-neutral-700)]';

interface TopNavProps {
  /** 개인정보 표시 동의 여부 — 상위에서 주입 (기본 false). 미지정 시 뱃지는 '미동의' */
  consented?: boolean;
  /** 미니멀 모드 — 프로젝트 드롭다운·네비 메뉴·동의 뱃지 숨김 (로고+언어+아바타만). 웰컴 페이지용 */
  minimal?: boolean;
}

function TopNav({ consented = false, minimal = false }: TopNavProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { projects, selectedId, setSelectedId } = useProjectContext();

  const [projectOpen, setProjectOpen] = useState(false);
  const [userOpen, setUserOpen] = useState(false);
  const projectRef = useRef<HTMLDivElement>(null);
  const userRef = useRef<HTMLDivElement>(null);

  const selected = projects.find((p) => p.id === selectedId) ?? projects[0];
  const userEmail = localStorage.getItem('user_email') ?? '';
  const initials = userEmail.slice(0, 2).toUpperCase() || 'ME';

  const { data: companyData } = useQuery({
    queryKey: ['company'],
    queryFn:  () => getCompanyInfo().then(r => r.data.data),
    staleTime: 5 * 60 * 1000,
  });
  const companyName = companyData?.companyName || null;

  /* 외부 클릭 시 드롭다운 닫기 */
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (projectRef.current && !projectRef.current.contains(e.target as Node)) setProjectOpen(false);
      if (userRef.current && !userRef.current.contains(e.target as Node)) setUserOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const activeItem =
    Object.entries(ROUTE_MAP).find(([, path]) => location.pathname === path)?.[0] ?? 'dashboard';

  const handleNavClick = (id: string) => {
    if (id === 'features') queryClient.invalidateQueries({ queryKey: ['users'] });
    navigate(ROUTE_MAP[id] ?? '/dashboard');
  };

  const handleLogout = () => {
    ['access_token', 'refresh_token', 'user_email', 'remembered_email', 'selected_project_id', 'selected_api_key']
      .forEach((k) => localStorage.removeItem(k));
    queryClient.clear();
    navigate('/login');
  };

  return (
    <header className="flex-shrink-0 h-[52px] bg-white border-b border-[var(--color-border-default)] z-[var(--z-sticky)]">
      <div className="h-full flex items-center justify-between gap-4 px-8">

        {/* ── 좌측: 로고 + 프로젝트 선택 ── */}
        <div className="flex items-center gap-6 min-w-0">
          <button type="button" onClick={() => navigate('/projects')} className="flex items-center flex-shrink-0">
            <Logo width={134} height={20} />
          </button>

          {!minimal && (
          <div ref={projectRef} className="relative flex-shrink-0">
            <button
              type="button"
              onClick={() => setProjectOpen((v) => !v)}
              className="flex items-center gap-2 w-[176px] px-2 py-1.5 bg-white border border-[var(--color-neutral-200)] rounded-[8px] hover:border-[var(--color-neutral-300)] transition-colors"
            >
              <span
                className="flex-shrink-0 w-5 h-5 rounded-[4px] border border-[var(--color-neutral-200)]"
                style={{ backgroundColor: selected?.colorTag || '#e2e8f0' }}
                aria-hidden
              />
              <span className="flex-1 min-w-0 text-left text-[14px] font-semibold text-[var(--color-neutral-800)] tracking-[-0.35px] leading-[20px] truncate">
                {selected?.name ?? '—'}
              </span>
              <ChevronIcon open={projectOpen} />
            </button>

            {projectOpen && (
              <div className="absolute left-0 top-full mt-1 w-[196px] bg-white border border-[var(--color-border-dropdown)] rounded-[8px] p-2 shadow-[var(--shadow-dropdown)] z-[var(--z-dropdown)]">
                {projects.filter((p) => p.id !== selected?.id).map((p) => (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => { setSelectedId(p.id); setProjectOpen(false); }}
                    className="flex items-center gap-2 w-full px-1.5 py-2 rounded-[8px] hover:bg-[var(--color-surface-layer1)] transition-colors"
                  >
                    <span
                      className="flex-shrink-0 w-5 h-5 rounded-[4px] border border-[var(--color-neutral-200)]"
                      style={{ backgroundColor: p.colorTag || '#e2e8f0' }}
                      aria-hidden
                    />
                    <span className="flex-1 min-w-0 text-left text-[14px] text-[var(--color-text-primary)] tracking-[-0.35px] leading-[20px] truncate">
                      {p.name}
                    </span>
                  </button>
                ))}
                <div className="my-1.5 border-t border-[var(--color-border-dropdown)]" />
                <button
                  type="button"
                  onClick={() => { navigate('/projects'); setProjectOpen(false); }}
                  className="flex w-full items-center px-2 py-2 rounded-[8px] hover:bg-[var(--color-surface-layer1)] transition-colors"
                >
                  <span className="text-[13px] text-[var(--color-text-tertiary)] leading-[16px]">
                    {t('projects.view_all')}
                  </span>
                </button>
              </div>
            )}
          </div>
          )}
        </div>

        {/* ── 중앙: 메뉴 (미니멀 모드에서는 숨김) ── */}
        {!minimal && (
        <nav className="flex items-center gap-5">
          {NAV_ITEMS.map((item) => {
            const isActive = item.id === activeItem;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => handleNavClick(item.id)}
                className={[NAV_ITEM_BASE, isActive ? NAV_ITEM_ACTIVE : NAV_ITEM_INACTIVE, 'text-[14px] tracking-[-0.35px] leading-[20px]'].join(' ')}
              >
                {t(item.i18nKey)}
              </button>
            );
          })}
        </nav>
        )}

        {/* ── 우측: 동의 뱃지 + 언어 + 프로필 ── */}
        <div className="flex items-center gap-3 flex-shrink-0">
          {!minimal && <ConsentBadge consented={consented} />}
          <LanguageSelect />

          <div ref={userRef} className="relative">
            <button
              type="button"
              onClick={() => setUserOpen((v) => !v)}
              className="flex-shrink-0 w-7 h-7 rounded-full bg-[var(--graph-color-3)] flex items-center justify-center text-white text-[10px] font-medium tracking-[-0.25px]"
            >
              {initials}
            </button>

            {userOpen && (
              <div className="absolute right-0 top-full mt-1.5 w-[200px] bg-white border border-[var(--color-border-dropdown)] rounded-[8px] p-3 shadow-[var(--shadow-dropdown)] z-[var(--z-dropdown)]">
                <div className="flex flex-col gap-0.5 px-1 pb-2 min-w-0">
                  {companyName && (
                    <span className="text-[13px] font-semibold text-[var(--color-neutral-800)] tracking-[-0.325px] truncate leading-[1.3]">
                      {companyName}
                    </span>
                  )}
                  <span className="text-[12px] text-[var(--color-text-secondary)] truncate leading-[1.3]">
                    {userEmail || '—'}
                  </span>
                </div>
                <div className="border-t border-[var(--color-border-dropdown)]" />
                <button
                  type="button"
                  onClick={handleLogout}
                  className="flex items-center gap-2 w-full px-1 pt-2.5 text-[var(--color-text-secondary)] hover:text-[var(--color-entry)] transition-colors"
                >
                  <LogoutIcon />
                  <span className="text-[13px] font-medium tracking-[-0.325px]">{t('common.logout')}</span>
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </header>
  );
}

export default TopNav;
