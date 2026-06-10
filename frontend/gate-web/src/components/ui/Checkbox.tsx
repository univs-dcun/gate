import { forwardRef } from 'react';
import type { InputHTMLAttributes } from 'react';

interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label?: string;
  indeterminate?: boolean;
}

/**
 * Figma 기반 Checkbox 컴포넌트
 * - 기본: 흰 배경 + neutral-300 테두리
 * - checked: Link_Blue 배경 + 흰 체크 아이콘
 * - indeterminate: 부분 선택 상태 (테이블 전체 선택 헤더용)
 * - disabled: neutral-100 배경, cursor-not-allowed
 */
const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(
  ({ label, indeterminate: _indeterminate, className = '', ...props }, ref) => {
    return (
      <label className={['inline-flex items-center gap-2 cursor-pointer select-none', props.disabled ? 'cursor-not-allowed opacity-50' : '', className].join(' ')}>
        <span className="relative flex items-center justify-center">
          <input
            ref={ref}
            type="checkbox"
            className="sr-only peer"
            {...props}
          />
          {/* 커스텀 박스 */}
          <span
            className="w-4 h-4 rounded-[var(--radius-sm)] border border-[var(--color-border-strong)]
                       bg-white transition-colors duration-[var(--transition-fast)]
                       peer-checked:bg-[var(--color-link-blue)] peer-checked:border-[var(--color-link-blue)]
                       peer-focus-visible:ring-2 peer-focus-visible:ring-[var(--color-border-focus)] peer-focus-visible:ring-offset-1"
          />
          {/* 체크 아이콘 (checked) */}
          <svg
            className="absolute w-2.5 h-2.5 text-white opacity-0 peer-checked:opacity-100 transition-opacity pointer-events-none"
            viewBox="0 0 12 10" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"
          >
            <polyline points="1 5 4.5 9 11 1" />
          </svg>
        </span>
        {label && (
          <span className="text-sm text-[var(--color-text-primary)]">{label}</span>
        )}
      </label>
    );
  }
);

Checkbox.displayName = 'Checkbox';
export default Checkbox;
