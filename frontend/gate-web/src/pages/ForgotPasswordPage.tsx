import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Input, Button } from '@/components/ui';
import { Header } from '@/components/layout';
import {
  sendPasswordResetCode,
  verifyPasswordResetCode,
  resetPassword,
} from '@/services/auth';

type Step = 'email' | 'verify' | 'password';

const CODE_LENGTH  = 8;
const RESEND_SECS  = 180;
const isValidEmail = (v: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);
const isValidPw    = (v: string) => v.length >= 8;

/* ── 공통 아이콘 ── */
const ErrorCircleIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" className="flex-shrink-0">
    <circle cx="12" cy="12" r="10" />
    <path d="M12 8v4" stroke="white" strokeWidth="2.5" strokeLinecap="round" />
    <path d="M12 16h.01" stroke="white" strokeWidth="2.5" strokeLinecap="round" />
  </svg>
);

const MailIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="2" y="4" width="20" height="16" rx="2" />
    <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
  </svg>
);

/* ── OTP 셀 ── */
interface OtpCellProps {
  value:     string;
  isFocused: boolean;
  hasError:  boolean;
  disabled:  boolean;
  inputRef:  (el: HTMLInputElement | null) => void;
  onChange:  (v: string) => void;
  onKeyDown: (e: React.KeyboardEvent<HTMLInputElement>) => void;
  onPaste:   (e: React.ClipboardEvent<HTMLInputElement>) => void;
  onFocus:   () => void;
}

function OtpCell({ value, isFocused, hasError, disabled, inputRef, onChange, onKeyDown, onPaste, onFocus }: OtpCellProps) {
  return (
    <input
      ref={inputRef}
      type="text"
      inputMode="numeric"
      maxLength={1}
      value={value}
      placeholder="0"
      disabled={disabled}
      onChange={(e) => onChange(e.target.value)}
      onKeyDown={onKeyDown}
      onPaste={onPaste}
      onFocus={onFocus}
      className={[
        'flex-1 min-w-0 h-12 text-center text-[24px] font-semibold rounded-[8px] border outline-none',
        'tracking-[-0.6px] leading-[1.4] transition-all duration-150',
        'placeholder:text-[var(--color-neutral-400)]',
        disabled
          ? 'bg-[var(--color-neutral-100)] text-[var(--color-neutral-400)] border-[var(--color-neutral-200)] cursor-not-allowed'
          : hasError
            ? value
              ? 'text-[#D83232] border-[#EF4444]'
              : 'text-[var(--color-neutral-400)] border-[#EF4444]'
            : value
              ? 'text-[var(--color-neutral-800)] border-[var(--color-neutral-300)]'
              : 'text-[var(--color-neutral-400)] border-[var(--color-neutral-300)]',
        isFocused && !hasError && !disabled
          ? 'border-[var(--color-link-blue)] shadow-[0_0_0_3px_rgba(0,111,255,0.12)]'
          : '',
      ].join(' ')}
    />
  );
}

/* ── 타이머 포맷 ── */
function formatTime(sec: number) {
  const m = Math.floor(sec / 60).toString().padStart(2, '0');
  const s = (sec % 60).toString().padStart(2, '0');
  return `${m}:${s}`;
}

/* ── Step 1: 이메일 입력 ── */
function EmailStep({
  onNext,
}: {
  onNext: (email: string) => void;
}) {
  const { t }           = useTranslation();
  const [email, setEmail]     = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState('');

  const emailTouched = email.length > 0;
  const emailValid   = isValidEmail(email);
  const showError    = emailTouched && !emailValid && email.length > 0;

  const handleSubmit = async () => {
    if (!emailValid || loading) return;
    setError('');
    setLoading(true);
    try {
      await sendPasswordResetCode(email);
      onNext(email);
    } catch (err) {
      const code = (err as { response?: { data?: { errors?: { code?: string } } } })
        ?.response?.data?.errors?.code;
      if (code === 'AUTH-112') {
        setError(t('forgot_password.error_no_account'));
      } else {
        setError(t('forgot_password.error_generic'));
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full max-w-[480px] flex flex-col gap-10 pb-20">
      {/* 타이틀 */}
      <div className="flex flex-col gap-2 text-center">
        <h1 className="text-[36px] font-semibold text-[var(--color-neutral-800)] tracking-[-0.9px] leading-[1.4]">
          {t('forgot_password.title')}
        </h1>
        <p className="text-[14px] font-normal text-[var(--color-neutral-800)] tracking-[0px] leading-[20px] whitespace-pre-line">
          {t('forgot_password.subtitle')}
        </p>
      </div>

      {/* 폼 */}
      <div className="flex flex-col gap-11">
        <div className="flex flex-col gap-1.5">
          <Input
            id="fp-email"
            type="email"
            label={t('forgot_password.email_label')}
            value={email}
            onChange={(v) => { setEmail(v); setError(''); }}
            placeholder="user@email.com"
            leftIcon={<MailIcon />}
            error={showError || !!error}
            autoComplete="email"
            disabled={loading}
          />
          {showError && (
            <p className="text-[13px] font-medium text-[var(--color-entry)] tracking-[-0.35px] leading-[1.4]">
              {t('signup.email_invalid')}
            </p>
          )}
          {error && !showError && (
            <div className="flex items-center gap-1.5 text-[#D83232]">
              <ErrorCircleIcon />
              <p className="text-[13px] font-medium tracking-[-0.35px] leading-[1.4]">{error}</p>
            </div>
          )}
        </div>

        <Button
          type="button"
          variant="primary"
          size="lg"
          fullWidth
          loading={loading}
          disabled={!emailValid}
          onClick={handleSubmit}
        >
          {t('forgot_password.send_code')}
        </Button>
      </div>

      {/* 문의하기 */}
      <div className="flex items-center justify-center gap-1 text-[16px]">
        <span className="font-medium text-[var(--color-neutral-800)] tracking-[-0.4px] leading-[1.4]">
          {t('forgot_password.contact_question')}
        </span>
        <a
          href="mailto:support@univs.ai"
          className="font-medium text-[var(--color-link-blue)] tracking-[-0.4px] leading-[1.4] hover:underline"
        >
          {t('forgot_password.contact_link')}
        </a>
      </div>
    </div>
  );
}

/* ── Step 2: 이메일 인증 ── */
function VerifyStep({
  email,
  onNext,
  onBack,
}: {
  email:  string;
  onNext: () => void;
  onBack: () => void;
}) {
  const { t } = useTranslation();
  const [digits, setDigits]         = useState<string[]>(Array(CODE_LENGTH).fill(''));
  const [focusedIdx, setFocusedIdx] = useState(0);
  const [countdown, setCountdown]   = useState(RESEND_SECS);
  const [loading, setLoading]       = useState(false);
  const [codeError, setCodeError]   = useState(false);
  const inputRefs = useRef<(HTMLInputElement | null)[]>(Array(CODE_LENGTH).fill(null));

  const isExpired = countdown <= 0;
  const canSubmit = digits.every((d) => d !== '') && !isExpired && !codeError;

  useEffect(() => { inputRefs.current[0]?.focus(); }, []);

  useEffect(() => {
    if (countdown <= 0) return;
    const id = setTimeout(() => setCountdown((c) => c - 1), 1000);
    return () => clearTimeout(id);
  }, [countdown]);

  const handleChange = (index: number, raw: string) => {
    if (!/^\d*$/.test(raw)) return;
    if (codeError) setCodeError(false);
    const digit = raw.slice(-1);
    const next  = [...digits];
    next[index] = digit;
    setDigits(next);
    if (digit && index < CODE_LENGTH - 1) inputRefs.current[index + 1]?.focus();
  };

  const handleKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace') {
      if (codeError) setCodeError(false);
      if (digits[index]) {
        const next = [...digits]; next[index] = ''; setDigits(next);
      } else if (index > 0) {
        const next = [...digits]; next[index - 1] = ''; setDigits(next);
        inputRefs.current[index - 1]?.focus();
      }
    } else if (e.key === 'ArrowLeft'  && index > 0)              inputRefs.current[index - 1]?.focus();
    else if   (e.key === 'ArrowRight' && index < CODE_LENGTH - 1) inputRefs.current[index + 1]?.focus();
  };

  const handlePaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    const text = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, CODE_LENGTH);
    if (!text) return;
    if (codeError) setCodeError(false);
    const next = [...digits];
    text.split('').forEach((d, i) => { next[i] = d; });
    setDigits(next);
    inputRefs.current[Math.min(text.length, CODE_LENGTH - 1)]?.focus();
  };

  const handleResend = async () => {
    if (countdown > 0) return;
    setDigits(Array(CODE_LENGTH).fill(''));
    setCodeError(false);
    try {
      await sendPasswordResetCode(email);
      setCountdown(RESEND_SECS);
      inputRefs.current[0]?.focus();
    } catch { /* 재발송 실패 시 만료 상태 유지 */ }
  };

  const handleSubmit = async () => {
    if (!canSubmit || loading) return;
    setLoading(true);
    try {
      const res = await verifyPasswordResetCode(email, digits.join(''));
      if (res.data.data.verified) { onNext(); return; }
      setCodeError(true);
    } catch (err) {
      const code = (err as { response?: { data?: { errors?: { code?: string } } } })
        ?.response?.data?.errors?.code;
      if (code === 'AUTH-118' || code === 'AUTH-117') {
        setCodeError(false); setCountdown(0);
      } else {
        setCodeError(true);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full max-w-[480px] flex flex-col items-center gap-10 pb-20">
      {/* 타이틀 */}
      <div className="flex flex-col gap-2 text-center w-full">
        <h1 className="text-[36px] font-semibold text-[var(--color-neutral-800)] tracking-[-0.9px] leading-[1.4]">
          {t('forgot_password.verify_title')}
        </h1>
        <p className="text-[14px] font-medium text-[var(--color-neutral-600)] tracking-[-0.35px] leading-[1.4] whitespace-pre-line">
          {t('forgot_password.verify_subtitle')}
        </p>
      </div>

      {/* 폼 */}
      <div className="w-full flex flex-col items-center gap-3">
        {/* OTP 8칸 */}
        <div className="flex w-full gap-2">
          {digits.map((digit, index) => (
            <OtpCell
              key={index}
              value={digit}
              isFocused={focusedIdx === index}
              hasError={codeError}
              disabled={isExpired}
              inputRef={(el) => { inputRefs.current[index] = el; }}
              onChange={(v) => handleChange(index, v)}
              onKeyDown={(e) => handleKeyDown(index, e)}
              onPaste={handlePaste}
              onFocus={() => setFocusedIdx(index)}
            />
          ))}
        </div>

        {/* 에러 / 만료 메시지 */}
        {codeError && (
          <div className="w-full flex items-center gap-1.5 text-[#D83232]">
            <ErrorCircleIcon />
            <p className="text-[14px] font-semibold tracking-[-0.35px] leading-[1.4]">
              {t('forgot_password.error_wrong_code')}
            </p>
          </div>
        )}
        {!codeError && isExpired && (
          <div className="w-full flex items-center gap-1.5 text-[#D83232]">
            <ErrorCircleIcon />
            <p className="text-[14px] font-semibold tracking-[-0.35px] leading-[1.4]">
              {t('forgot_password.error_expired')}
            </p>
          </div>
        )}

        <div className="w-full flex flex-col gap-8 mt-1">
          {/* 재발송 */}
          <div className="flex items-center justify-center gap-1 text-[14px]">
            <span className="font-normal text-[var(--color-neutral-800)]">
              {t('forgot_password.no_code')}
            </span>
            <button
              type="button"
              onClick={handleResend}
              disabled={countdown > 0}
              className={[
                'font-semibold transition-colors',
                countdown > 0
                  ? 'text-[var(--color-text-disabled)] cursor-not-allowed'
                  : 'text-[var(--color-link-blue)] hover:text-[var(--color-link-blue-hover)]',
              ].join(' ')}
            >
              {countdown > 0
                ? `${t('forgot_password.resend')} (${formatTime(countdown)})`
                : t('forgot_password.resend')}
            </button>
          </div>

          {/* 다음 버튼 */}
          <Button
            type="button"
            variant="primary"
            size="lg"
            fullWidth
            loading={loading}
            disabled={!canSubmit}
            onClick={handleSubmit}
          >
            {t('forgot_password.next')}
          </Button>
        </div>

        {/* 이메일 재입력 */}
        <button
          type="button"
          onClick={onBack}
          className="text-[16px] font-medium text-[var(--color-neutral-800)] tracking-[-0.4px] leading-[1.4] hover:text-[var(--color-link-blue)] transition-colors"
        >
          {t('forgot_password.change_email')}
        </button>
      </div>
    </div>
  );
}

/* ── Step 3: 새 비밀번호 입력 ── */
function NewPasswordStep({
  email,
  onDone,
}: {
  email:  string;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [password, setPassword]               = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordTouched, setPasswordTouched] = useState(false);
  const [confirmTouched, setConfirmTouched]   = useState(false);
  const [loading, setLoading]                 = useState(false);
  const [apiError, setApiError]               = useState('');

  const passwordValid    = isValidPw(password);
  const confirmValid     = password === confirmPassword;
  const canSubmit        = passwordValid && confirmValid && confirmPassword.length > 0;
  const showPwError      = passwordTouched && password.length > 0 && !passwordValid;
  const showConfirmError = confirmTouched && confirmPassword.length > 0 && !confirmValid;

  const handleSubmit = async () => {
    if (!canSubmit || loading) return;
    setApiError('');
    setLoading(true);
    try {
      await resetPassword(email, password, confirmPassword);
      onDone();
    } catch {
      setApiError(t('forgot_password.error_generic'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full max-w-[480px] flex flex-col gap-10 pb-20">
      {/* 타이틀 */}
      <div className="flex flex-col gap-2 text-center">
        <h1 className="text-[36px] font-semibold text-[var(--color-neutral-800)] tracking-[-0.9px] leading-[1.4]">
          {t('forgot_password.new_password_title')}
        </h1>
        <p className="text-[14px] font-medium text-[var(--color-neutral-600)] tracking-[-0.35px] leading-[1.4]">
          {t('forgot_password.new_password_subtitle')}
        </p>
      </div>

      {/* 폼 */}
      <div className="flex flex-col gap-8">
        <div className="flex flex-col gap-1.5">
          <Input
            id="fp-new-password"
            type="password"
            label={t('forgot_password.password_label')}
            value={password}
            onChange={(v) => { setPassword(v); setApiError(''); }}
            onBlur={() => { if (password.length > 0) setPasswordTouched(true); }}
            placeholder={t('forgot_password.password_placeholder')}
            error={showPwError}
            autoComplete="new-password"
          />
          {showPwError && (
            <p className="text-[13px] font-medium text-[var(--color-entry)] tracking-[-0.35px] leading-[1.4]">
              {t('forgot_password.password_invalid')}
            </p>
          )}
        </div>

        <div className="flex flex-col gap-1.5">
          <Input
            id="fp-confirm-password"
            type="password"
            label={t('forgot_password.confirm_label')}
            value={confirmPassword}
            onChange={(v) => { setConfirmPassword(v); setApiError(''); }}
            onBlur={() => { if (confirmPassword.length > 0) setConfirmTouched(true); }}
            placeholder={t('forgot_password.confirm_placeholder')}
            error={showConfirmError}
            autoComplete="new-password"
          />
          {showConfirmError && (
            <p className="text-[13px] font-medium text-[var(--color-entry)] tracking-[-0.35px] leading-[1.4]">
              {t('forgot_password.confirm_invalid')}
            </p>
          )}
        </div>

        {apiError && (
          <div className="flex items-center gap-1.5 text-[#D83232]">
            <ErrorCircleIcon />
            <p className="text-[13px] font-medium tracking-[-0.35px] leading-[1.4]">{apiError}</p>
          </div>
        )}

        <Button
          type="button"
          variant="primary"
          size="lg"
          fullWidth
          loading={loading}
          disabled={!canSubmit}
          onClick={handleSubmit}
        >
          {t('forgot_password.submit')}
        </Button>
      </div>
    </div>
  );
}

/* ── 페이지 ── */
function ForgotPasswordPage() {
  const navigate = useNavigate();
  const [step, setStep]   = useState<Step>('email');
  const [email, setEmail] = useState('');

  const handleEmailNext = (e: string) => {
    setEmail(e);
    setStep('verify');
  };

  const handleVerifyNext = () => setStep('password');

  const handleDone = () => {
    navigate('/login', { state: { toast: 'password_changed' } });
  };

  return (
    <div className="min-h-screen bg-white flex flex-col">
      <Header hideAuthButtons />

      <div
        className="flex-1 flex justify-center px-4"
        style={{ paddingTop: 'calc(var(--header-height) + 140px)' }}
      >
        {step === 'email' && (
          <EmailStep onNext={handleEmailNext} />
        )}
        {step === 'verify' && (
          <VerifyStep
            email={email}
            onNext={handleVerifyNext}
            onBack={() => setStep('email')}
          />
        )}
        {step === 'password' && (
          <NewPasswordStep email={email} onDone={handleDone} />
        )}
      </div>
    </div>
  );
}

export default ForgotPasswordPage;
