import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { changeLanguage } from '@/i18n';
import { Logo } from '@/components/ui';

type Language = 'ko' | 'en';
const LANGUAGES: { code: Language; label: string }[] = [
  { code: 'ko', label: '한국어' },
  { code: 'en', label: 'English' },
];

/* ─── 아이콘 ─────────────────────────────────────────────── */
const GlobeIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <circle cx="12" cy="12" r="10" />
    <line x1="2" y1="12" x2="22" y2="12" />
    <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
  </svg>
);
const ChevronDownIcon = () => (
  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="6 9 12 15 18 9" />
  </svg>
);
const CheckIcon = () => (
  <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="20 6 9 17 4 12" />
  </svg>
);

/* ─── 언어 선택 ───────────────────────────────────────────── */
function LanguageSelect() {
  const { i18n } = useTranslation();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const currentLang = (i18n.language ?? 'ko') as Language;
  const current = LANGUAGES.find((l) => l.code === currentLang) ?? LANGUAGES[0];

  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className={[
          'flex items-center gap-1.5 h-8 px-2.5 text-xs font-medium rounded-[var(--radius-md)]',
          'border transition-colors duration-[var(--transition-fast)] select-none',
          open
            ? 'bg-[var(--color-neutral-100)] border-[var(--color-border-default)] text-[var(--color-text-primary)]'
            : 'bg-transparent border-[var(--color-border-default)] text-[var(--color-text-secondary)] hover:bg-[var(--color-neutral-50)] hover:text-[var(--color-text-primary)]',
        ].join(' ')}
        aria-haspopup="listbox"
        aria-expanded={open}
      >
        <span className="text-[var(--color-text-disabled)]"><GlobeIcon /></span>
        <span>{current.label}</span>
        <span className={['text-[var(--color-text-disabled)] transition-transform duration-150', open ? 'rotate-180' : ''].join(' ')}>
          <ChevronDownIcon />
        </span>
      </button>

      {open && (
        <div
          role="listbox"
          className="absolute top-full right-0 mt-1 min-w-[130px] bg-[var(--color-bg-page)] border border-[var(--color-border-default)] rounded-[var(--radius-lg)] shadow-[var(--shadow-md)] overflow-hidden z-[var(--z-dropdown)] py-1"
        >
          {LANGUAGES.map((lang) => {
            const isSelected = lang.code === currentLang;
            return (
              <button
                key={lang.code}
                role="option"
                aria-selected={isSelected}
                type="button"
                onClick={() => { changeLanguage(lang.code); setOpen(false); }}
                className={[
                  'w-full flex items-center justify-between gap-3 px-3 py-2 text-xs font-medium',
                  'transition-colors duration-[var(--transition-fast)] text-left',
                  isSelected
                    ? 'text-[var(--color-link-blue)] bg-[var(--color-blue-10)]'
                    : 'text-[var(--color-text-primary)] hover:bg-[var(--color-neutral-50)]',
                ].join(' ')}
              >
                {lang.label}
                {isSelected && <span className="text-[var(--color-link-blue)]"><CheckIcon /></span>}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

/* ─── 헤더 ───────────────────────────────────────────────── */
function MobileHeader() {
  return (
    <header
      className="flex flex-col justify-end flex-shrink-0 border-b border-[var(--color-border-default)] bg-white"
      style={{ paddingTop: 'env(safe-area-inset-top)' }}
    >
      <div className="flex items-center justify-between px-5 h-14">
        <Logo height={21} />
        <LanguageSelect />
      </div>
    </header>
  );
}

/* ─── MobileLayout ───────────────────────────────────────── */
interface MobileLayoutProps {
  children: ReactNode;
  /** 배경색 (기본: 흰색) */
  bg?: string;
}

/**
 * 모바일 전용 레이아웃
 * - 모바일(≤480px): 전체 화면 꽉 채움
 * - 데스크탑(>480px): 430px 센터 정렬 + 폰 프레임 쉐도우
 * - Safe Area (iOS 노치/홈바) 자동 처리
 */
function MobileLayout({ children, bg = '#ffffff' }: MobileLayoutProps) {
  useEffect(() => {
    const prev = document.body.style.overscrollBehavior;
    document.body.style.overscrollBehavior = 'none';
    return () => { document.body.style.overscrollBehavior = prev; };
  }, []);

  return (
    /* 외부: 전체 뷰포트 — 데스크탑에서 폰 프레임 배경 */
    <div className="h-[100dvh] w-full flex items-center justify-center bg-[#e8ecf2]">
      {/* 내부: 모바일 컨테이너
          (max-width: 480px) → w-full 전체 화면
          (min-width: 481px) → max-w-[430px] 센터 정렬 + 그림자 */}
      <div
        className="h-full w-full flex flex-col overflow-hidden min-[481px]:max-w-[430px] min-[481px]:shadow-2xl"
        style={{ backgroundColor: bg }}
      >
        <MobileHeader />
        {children}
      </div>
    </div>
  );
}

export default MobileLayout;
