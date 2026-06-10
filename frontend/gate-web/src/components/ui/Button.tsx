import { forwardRef } from 'react';
import type { ButtonHTMLAttributes } from 'react';
import type { Size, Variant } from '@/types';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  fullWidth?: boolean;
}

/**
 * Figma 디자인 기반 Button 컴포넌트
 *
 * Variant:
 *   primary  — Link_Blue 배경 (로그인, 주요 CTA)
 *   secondary — neutral_100 배경 (보조 액션)
 *   outline  — 테두리만 (회원가입)
 *   ghost    — 배경 없음
 *   danger   — entry_Rg 배경 (삭제, 위험 액션)
 *
 * Size: xs | sm | md | lg | xl
 * State: default / hover / active / disabled / loading
 */

const variantStyles: Record<Variant, string> = {
  primary: [
    'bg-[var(--color-link-blue)] text-[var(--color-text-inverse)]',
    'hover:bg-[var(--color-link-blue-hover)]',
    'active:bg-[var(--color-link-blue-active)]',
    'border border-transparent',
  ].join(' '),

  secondary: [
    'bg-[var(--color-neutral-100)] text-[var(--color-text-primary)]',
    'hover:bg-[var(--color-neutral-200)]',
    'active:bg-[var(--color-neutral-300)]',
    'border border-transparent',
  ].join(' '),

  outline: [
    'bg-transparent text-[var(--color-link-blue)]',
    'border border-[var(--color-link-blue)]',
    'hover:bg-[var(--color-blue-10)]',
    'active:bg-[var(--color-blue-40)]',
  ].join(' '),

  ghost: [
    'bg-transparent text-[var(--color-text-primary)]',
    'hover:bg-[var(--color-neutral-100)]',
    'active:bg-[var(--color-neutral-200)]',
    'border border-transparent',
  ].join(' '),

  danger: [
    'bg-[var(--color-entry)] text-[var(--color-text-inverse)]',
    'hover:bg-[var(--color-entry-hover)]',
    'active:bg-[var(--color-entry-active)]',
    'border border-transparent',
  ].join(' '),
};

const sizeStyles: Record<Size, string> = {
  xs: 'h-6  px-2   text-xs   rounded-[var(--radius-sm)] gap-1',
  sm: 'h-8  px-3   text-sm   rounded-[var(--radius-md)] gap-1.5',
  md: 'h-9  px-4   text-sm   rounded-[var(--radius-md)] gap-2',
  lg: 'h-10 px-5   text-base rounded-[var(--radius-lg)] gap-2',
  xl: 'h-12 px-6   text-base rounded-[var(--radius-lg)] gap-2',
};

const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      variant = 'primary',
      size = 'md',
      loading = false,
      fullWidth = false,
      disabled,
      className = '',
      children,
      ...props
    },
    ref
  ) => {
    const isDisabled = disabled || loading;

    return (
      <button
        ref={ref}
        disabled={isDisabled}
        className={[
          'inline-flex items-center justify-center font-medium',
          'transition-colors duration-[var(--transition-fast)]',
          'focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-border-focus)] focus-visible:ring-offset-2',
          'disabled:bg-[var(--color-neutral-200)] disabled:text-[var(--color-text-disabled)] disabled:border-transparent disabled:cursor-not-allowed',
          variantStyles[variant],
          sizeStyles[size],
          fullWidth ? 'w-full' : '',
          className,
        ]
          .filter(Boolean)
          .join(' ')}
        {...props}
      >
        {loading && (
          <svg
            className="h-4 w-4 animate-spin"
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <circle
              className="opacity-25"
              cx="12" cy="12" r="10"
              stroke="currentColor"
              strokeWidth="4"
            />
            <path
              className="opacity-75"
              fill="currentColor"
              d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
            />
          </svg>
        )}
        {children}
      </button>
    );
  }
);

Button.displayName = 'Button';

export default Button;
