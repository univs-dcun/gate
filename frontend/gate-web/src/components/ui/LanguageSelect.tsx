import { useState, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { LanguageIcon } from '@/components/ui/icons';
import { changeLanguage } from '@/i18n';

/**
 * LanguageSelect — GNB용 컴팩트 로케일 셀렉터
 *
 * Figma node 1735:8333 (GNB) 기반. 글로브 + 언어코드(KO/EN) + chevron.
 *  - 트리거: 흰 박스, border neutral-200, rounded-8, px-8 py-6
 *  - 드롭다운: 한국어 / English (현재 선택 강조)
 */

const LANG_OPTIONS = [
  { code: 'ko', code_label: 'KO', label: '한국어', sub: 'Korean' },
  { code: 'en', code_label: 'EN', label: 'English', sub: '영어' },
] as const;

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

function CheckIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <polyline points="20 6 9 17 4 12" />
    </svg>
  );
}

export default function LanguageSelect({ className }: { className?: string }) {
  const { i18n } = useTranslation();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const currentLang = i18n.language as 'ko' | 'en';
  const current = LANG_OPTIONS.find((l) => l.code === currentLang) ?? LANG_OPTIONS[0];

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  return (
    <div ref={ref} className={['relative', className ?? ''].join(' ')}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex items-center gap-2 px-2 py-1.5 bg-white border border-[var(--color-neutral-200)] rounded-[8px] hover:border-[var(--color-neutral-300)] transition-colors"
      >
        <span className="flex items-center gap-1">
          <LanguageIcon size={16} />
          <span className="text-[14px] font-semibold text-[var(--color-neutral-700)] tracking-[-0.35px] leading-[20px]">
            {current.code_label}
          </span>
        </span>
        <ChevronIcon open={open} />
      </button>

      {open && (
        <div className="absolute right-0 top-full mt-1 min-w-[140px] bg-white border border-[var(--color-border-dropdown)] rounded-[8px] p-1 shadow-[var(--shadow-dropdown)] overflow-hidden z-[var(--z-dropdown)]">
          {LANG_OPTIONS.map((opt) => {
            const selected = opt.code === currentLang;
            return (
              <button
                key={opt.code}
                type="button"
                onClick={() => { changeLanguage(opt.code); setOpen(false); }}
                className={[
                  'flex items-center justify-between w-full gap-2 px-3 py-2 rounded-[6px] text-[13px] tracking-[-0.325px] transition-colors',
                  selected
                    ? 'bg-[var(--color-primary-100)] text-[var(--color-primary-400)] font-semibold'
                    : 'text-[var(--color-neutral-600)] hover:bg-[var(--color-surface-layer1)]',
                ].join(' ')}
              >
                <span className="whitespace-nowrap">
                  {opt.label} <span className="text-[var(--color-neutral-400)]">/ {opt.sub}</span>
                </span>
                {selected && <CheckIcon />}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
