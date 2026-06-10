import { useState } from 'react';
import type { ReactNode } from 'react';
import IconSidebar from './IconSidebar';

/* ── SVG 아이콘 (인라인, 외부 라이브러리 없이) ── */
const HomeIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z" /><polyline points="9 22 9 12 15 12 15 22" />
  </svg>
);
const GridIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="3" width="7" height="7" /><rect x="14" y="3" width="7" height="7" />
    <rect x="14" y="14" width="7" height="7" /><rect x="3" y="14" width="7" height="7" />
  </svg>
);
const FileIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" /><polyline points="14 2 14 8 20 8" />
  </svg>
);
const BellIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M18 8A6 6 0 006 8c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.73 21a2 2 0 01-3.46 0" />
  </svg>
);
const SettingsIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="3" />
    <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z" />
  </svg>
);
const UserIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2" /><circle cx="12" cy="7" r="4" />
  </svg>
);
const ChevronDownIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="6 9 12 15 18 9" />
  </svg>
);

const NAV_ITEMS = [
  { id: 'home',       icon: <HomeIcon />,     label: '내 프로젝트' },
  { id: 'projects',   icon: <GridIcon />,     label: '프로젝트 생성' },
  { id: 'files',      icon: <FileIcon />,     label: '파일 목록' },
  { id: 'alerts',     icon: <BellIcon />,     label: '알림' },
  { id: 'settings',   icon: <SettingsIcon />, label: '설정' },
];

const BOTTOM_ITEMS = [
  { id: 'profile', icon: <UserIcon />, label: '내 프로필' },
];

interface AppLayoutProps {
  children: ReactNode;
  pageTitle?: string;
}

/**
 * Figma 메인 화면 기반 인증 후 레이아웃
 *
 * [IconSidebar 52px] | [Header 60px + 콘텐츠]
 */
function AppLayout({ children, pageTitle = '내 프로젝트' }: AppLayoutProps) {
  const [activeNav, setActiveNav] = useState('home');
  const [lang, setLang] = useState<'ko' | 'en'>('ko');

  return (
    <div className="flex h-screen overflow-hidden bg-[var(--color-bg-surface)] min-w-[1280px] max-w-[1920px]">
      {/* 좌측 아이콘 사이드바 */}
      <IconSidebar
        items={NAV_ITEMS}
        bottomItems={BOTTOM_ITEMS}
        activeId={activeNav}
        onSelect={setActiveNav}
      />

      {/* 메인 영역 (사이드바 오른쪽 전체) */}
      <div
        className="flex flex-col flex-1 min-w-0 overflow-hidden"
        style={{ marginLeft: 'var(--icon-sidebar-width)' }}
      >
        {/* 상단 헤더 */}
        <header
          className="flex-shrink-0 flex items-center justify-between px-6 bg-[var(--color-bg-page)] border-b border-[var(--color-border-default)] z-[var(--z-sticky)]"
          style={{ height: 'var(--header-height)' }}
        >
          {/* 좌측: 서비스명 + 페이지 타이틀 */}
          <div className="flex items-center gap-3">
            <button className="flex items-center gap-1.5 font-bold text-[length:var(--text-base)] text-[var(--color-text-primary)]">
              UNIVS GATE
              <ChevronDownIcon />
            </button>
            <span className="text-[var(--color-neutral-300)]">/</span>
            <span className="text-sm text-[var(--color-text-secondary)]">{pageTitle}</span>
          </div>

          {/* 우측: 언어 전환 + 알림 + 아바타 */}
          <div className="flex items-center gap-3">
            {/* 언어 토글 */}
            <div className="flex items-center rounded-[var(--radius-md)] border border-[var(--color-border-default)] overflow-hidden">
              {(['ko', 'en'] as const).map((code) => (
                <button
                  key={code}
                  onClick={() => setLang(code)}
                  className={[
                    'px-3 py-1 text-xs font-medium transition-colors duration-[var(--transition-fast)]',
                    lang === code
                      ? 'bg-[var(--color-link-blue)] text-white'
                      : 'bg-transparent text-[var(--color-text-secondary)] hover:bg-[var(--color-neutral-100)]',
                  ].join(' ')}
                >
                  {code.toUpperCase()}
                </button>
              ))}
            </div>

            {/* 알림 벨 */}
            <button className="flex h-8 w-8 items-center justify-center rounded-[var(--radius-md)] text-[var(--color-text-secondary)] hover:bg-[var(--color-neutral-100)] transition-colors">
              <BellIcon />
            </button>

            {/* 유저 아바타 */}
            <button className="h-8 w-8 rounded-full bg-[var(--color-blue-40)] flex items-center justify-center text-[var(--color-link-blue)] font-semibold text-sm">
              U
            </button>
          </div>
        </header>

        {/* 스크롤 가능한 콘텐츠 */}
        <main className="flex-1 overflow-y-auto">
          {children}
        </main>
      </div>
    </div>
  );
}

export default AppLayout;
