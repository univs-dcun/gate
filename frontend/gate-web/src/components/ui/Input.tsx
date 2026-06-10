import { useState, forwardRef } from 'react';
import type { InputHTMLAttributes, ReactElement } from 'react';
import { VisibilityIcon, VisibilityOffIcon } from './icons';
const XCircleIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10" />
    <line x1="15" y1="9" x2="9" y2="15" />
    <line x1="9" y1="9" x2="15" y2="15" />
  </svg>
);

export interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange' | 'value' | 'type'> {
  value: string;
  onChange: (value: string) => void;
  type?: 'text' | 'email' | 'password';
  label?: string;
  leftIcon?: ReactElement;
  error?: boolean;
}

/**
 * Figma 로그인 화면 기반 Input 컴포넌트
 *
 * 상태:
 *   default  — neutral border
 *   focus    — Link_Blue border + subtle ring
 *   error    — entry(red) border
 *   disabled — neutral-50 배경 + cursor-not-allowed
 *
 * 기능:
 *   - leftIcon  — 아이콘 영역 (사용자, 잠금 등)
 *   - clear     — 값이 있을 때 X 버튼 표시
 *   - password  — 눈 아이콘으로 가시성 토글
 */
const Input = forwardRef<HTMLInputElement, InputProps>(
  (
    {
      value,
      onChange,
      type = 'text',
      label,
      leftIcon,
      error = false,
      disabled = false,
      id,
      placeholder,
      className = '',
      ...rest
    },
    ref
  ) => {
    const [showPassword, setShowPassword] = useState(false);
    const inputType = type === 'password' ? (showPassword ? 'text' : 'password') : type;
    const hasValue = value.length > 0;

    /* 오른쪽 아이콘 수 기반 padding */
    const rightPadding =
      type === 'password'
        ? hasValue ? 'pr-[4.5rem]' : 'pr-10'
        : hasValue ? 'pr-9' : 'pr-3.5';

    const borderClass = error
      ? 'border-[var(--color-entry)] focus:border-[var(--color-entry)] focus:shadow-[0_0_0_3px_rgba(239,68,68,0.15)]'
      : 'border-[var(--color-border-default)] focus:border-[var(--color-link-blue)] focus:shadow-[0_0_0_3px_rgba(37,99,235,0.12)]';

    return (
      <div className={['flex flex-col gap-1.5', className].join(' ')}>
        {/* 라벨 */}
        {label && (
          <label
            htmlFor={id}
            className="text-sm font-medium text-[var(--color-text-primary)]"
          >
            {label}
          </label>
        )}

        {/* 입력 영역 */}
        <div className="relative flex items-center">
          {/* 좌측 아이콘 */}
          {leftIcon && (
            <span className="absolute left-3 text-[var(--color-text-disabled)] pointer-events-none">
              {leftIcon}
            </span>
          )}

          <input
            ref={ref}
            id={id}
            type={inputType}
            value={value}
            onChange={(e) => onChange(e.target.value)}
            placeholder={placeholder}
            disabled={disabled}
            className={[
              'w-full h-11 text-sm border rounded-[var(--radius-lg)] outline-none',
              'text-[var(--color-text-primary)] placeholder:text-[var(--color-text-disabled)]',
              'transition-all duration-[var(--transition-fast)]',
              leftIcon ? 'pl-10' : 'pl-3.5',
              rightPadding,
              borderClass,
              disabled
                ? 'bg-[var(--color-neutral-50)] cursor-not-allowed'
                : 'bg-white cursor-text',
            ]
              .filter(Boolean)
              .join(' ')}
            {...rest}
          />

          {/* 우측 액션 버튼 */}
          <div className="absolute right-2 flex items-center gap-0.5">
            {/* Clear 버튼 */}
            {hasValue && (
              <button
                type="button"
                tabIndex={-1}
                onClick={() => onChange('')}
                aria-label="입력 내용 지우기"
                className="flex h-6 w-6 items-center justify-center rounded-full
                           text-[var(--color-text-disabled)] hover:text-[var(--color-text-secondary)]
                           hover:bg-[var(--color-neutral-100)] transition-colors"
              >
                <XCircleIcon />
              </button>
            )}
            {/* 비밀번호 토글 */}
            {type === 'password' && (
              <button
                type="button"
                tabIndex={-1}
                onClick={() => setShowPassword((v) => !v)}
                aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
                className="flex h-6 w-6 items-center justify-center rounded-full
                           text-[var(--color-text-disabled)] hover:text-[var(--color-text-secondary)]
                           hover:bg-[var(--color-neutral-100)] transition-colors"
              >
                {showPassword ? <VisibilityIcon size={15} /> : <VisibilityOffIcon size={15} />}
              </button>
            )}
          </div>
        </div>
      </div>
    );
  }
);

Input.displayName = 'Input';
export default Input;
