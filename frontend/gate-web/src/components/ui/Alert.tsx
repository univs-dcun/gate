import { useEffect } from 'react';
import type { ReactNode } from 'react';

export type AlertVariant = 'success' | 'error';

interface AlertProps {
  message:  string;
  variant?: AlertVariant;
  onClose:  () => void;
  duration?: number; // ms, 0 = no auto-close
}

const VARIANT_STYLE: Record<AlertVariant, { wrap: string; icon: ReactNode }> = {
  success: {
    wrap: 'bg-[#f0fdf4] border border-[#86efac] text-[#15803d]',
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <polyline points="20 6 9 17 4 12" />
      </svg>
    ),
  },
  error: {
    wrap: 'bg-[#fff7f6] border border-[#fca5a5] text-[#d83232]',
    icon: (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="12" r="10" /><line x1="15" y1="9" x2="9" y2="15" /><line x1="9" y1="9" x2="15" y2="15" />
      </svg>
    ),
  },
};

export default function Alert({ message, variant = 'success', onClose, duration = 3000 }: AlertProps) {
  useEffect(() => {
    if (!duration) return;
    const t = setTimeout(onClose, duration);
    return () => clearTimeout(t);
  }, [duration, onClose]);

  const { wrap, icon } = VARIANT_STYLE[variant];

  return (
    <div className="fixed top-6 left-1/2 -translate-x-1/2 z-[9999] pointer-events-none">
      <div className={['flex items-center gap-2.5 px-4 py-3 rounded-[10px] shadow-lg min-w-[280px] pointer-events-auto', wrap].join(' ')}>
        <span className="flex-shrink-0">{icon}</span>
        <span className="text-[14px] font-medium tracking-[-0.35px] flex-1">{message}</span>
        <button
          onClick={onClose}
          className="flex-shrink-0 opacity-60 hover:opacity-100 transition-opacity"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>
    </div>
  );
}
