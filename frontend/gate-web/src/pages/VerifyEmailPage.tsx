import { useState, useRef, useEffect } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui';
import { Header } from '@/components/layout';
import { sendEmailCode, verifyEmailCode } from '@/services/auth';

const CODE_LENGTH  = 8;
const RESEND_SECS  = 180; // 3분

/* ── 타이머 포맷 ─────────────────────────────────────────── */
function formatTime(sec: number) {
  const m = Math.floor(sec / 60).toString().padStart(2, '0');
  const s = (sec % 60).toString().padStart(2, '0');
  return `${m}:${s}`;
}

/* ── 에러 아이콘 ─────────────────────────────────────────── */
const ErrorCircleIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" className="flex-shrink-0">
    <circle cx="12" cy="12" r="10" />
    <path d="M12 8v4" stroke="white" strokeWidth="2.5" strokeLinecap="round" />
    <path d="M12 16h.01" stroke="white" strokeWidth="2.5" strokeLinecap="round" />
  </svg>
);

/* ── OTP 셀 컴포넌트 ─────────────────────────────────────── */
interface OtpCellProps {
  value: string;
  isFocused: boolean;
  hasError: boolean;
  disabled: boolean;
  inputRef: (el: HTMLInputElement | null) => void;
  onChange: (v: string) => void;
  onKeyDown: (e: React.KeyboardEvent<HTMLInputElement>) => void;
  onPaste: (e: React.ClipboardEvent<HTMLInputElement>) => void;
  onFocus: () => void;
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
        'w-12 bg-white border rounded-[8px] text-center text-[24px] font-semibold outline-none',
        'flex-1 h-12 text-center text-[24px] font-semibold rounded-[8px] border outline-none',
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

/* ── 페이지 ─────────────────────────────────────────────── */
function VerifyEmailPage() {
  const { t }    = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const email    = (location.state as { email?: string } | null)?.email ?? '';

  const [digits, setDigits]         = useState<string[]>(Array(CODE_LENGTH).fill(''));
  const [focusedIdx, setFocusedIdx] = useState<number>(0);
  const [countdown, setCountdown]   = useState(RESEND_SECS);
  const [loading, setLoading]       = useState(false);
  const [codeError, setCodeError]   = useState(false);
  const inputRefs = useRef<(HTMLInputElement | null)[]>(Array(CODE_LENGTH).fill(null));

  const isExpired = countdown <= 0;

  /* 이메일 없이 직접 접근 시 signup으로 리다이렉트 */
  useEffect(() => {
    if (!email) navigate('/signup', { replace: true });
  }, [email, navigate]);

  /* 첫 렌더 시 자동 포커스 */
  useEffect(() => { inputRefs.current[0]?.focus(); }, []);

  /* 카운트다운 */
  useEffect(() => {
    if (countdown <= 0) return;
    const id = setTimeout(() => setCountdown((c) => c - 1), 1000);
    return () => clearTimeout(id);
  }, [countdown]);

  const canSubmit = digits.every((d) => d !== '') && !isExpired && !codeError;

  /* ── 입력 핸들러 ── */
  const handleChange = (index: number, raw: string) => {
    if (!/^\d*$/.test(raw)) return;
    if (codeError) setCodeError(false);
    const digit = raw.slice(-1);
    const next  = [...digits];
    next[index] = digit;
    setDigits(next);
    if (digit && index < CODE_LENGTH - 1) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handleKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace') {
      if (codeError) setCodeError(false);
      if (digits[index]) {
        const next = [...digits];
        next[index] = '';
        setDigits(next);
      } else if (index > 0) {
        const next = [...digits];
        next[index - 1] = '';
        setDigits(next);
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
    const focusIdx = Math.min(text.length, CODE_LENGTH - 1);
    inputRefs.current[focusIdx]?.focus();
  };

  const handleResend = async () => {
    if (countdown > 0) return;
    setDigits(Array(CODE_LENGTH).fill(''));
    setCodeError(false);
    try {
      await sendEmailCode(email);
      setCountdown(RESEND_SECS);
      inputRefs.current[0]?.focus();
    } catch {
      // 재발송 실패 시 만료 상태 유지
    }
  };

  const handleSubmit = async () => {
    if (!canSubmit || loading) return;
    setLoading(true);
    try {
      const res = await verifyEmailCode(email, digits.join(''));
      if (res.data.data.verified) {
        navigate('/set-password', { state: { email } });
        return;
      }
      setCodeError(true);
    } catch (err) {
      const code = (err as { response?: { data?: { errors?: { code?: string } } } })
        ?.response?.data?.errors?.code;
      /* 만료 / 인증 기록 없음 → 만료 상태로 전환 */
      if (code === 'AUTH-118' || code === 'AUTH-117') {
        setCodeError(false);
        setCountdown(0);
      } else {
        /* AUTH-120(코드 불일치), AUTH-119(시도 초과), 기타 → 에러 상태 */
        setCodeError(true);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-white flex flex-col">
      <Header hideAuthButtons />

      <div
        className="flex-1 flex justify-center px-4"
        style={{ paddingTop: 'calc(var(--header-height) + 140px)' }}
      >
        <div className="w-full max-w-[480px] flex flex-col items-center gap-10 pb-20">

          {/* 타이틀 */}
          <div className="flex flex-col gap-2 text-center">
            <h1 className="text-[36px] font-semibold text-[var(--color-neutral-800)] tracking-[-0.9px] leading-[1.4]">
              {t('verify.title')}
            </h1>
            <p className="text-[14px] font-medium text-[var(--color-neutral-600)] tracking-[-0.35px] leading-[1.4] whitespace-pre-line">
              {t('verify.subtitle')}
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
                  {t('verify.error_wrong_code')}
                </p>
              </div>
            )}
            {!codeError && isExpired && (
              <div className="w-full flex items-center gap-1.5 text-[#D83232]">
                <ErrorCircleIcon />
                <p className="text-[14px] font-semibold tracking-[-0.35px] leading-[1.4]">
                  {t('verify.error_expired')}
                </p>
              </div>
            )}

            {/* 재발송 + 다음 버튼 그룹 */}
            <div className="w-full flex flex-col gap-8 mt-1">

              {/* 재발송 */}
              <div className="flex items-center justify-center gap-1 text-[14px]">
                <span className="font-medium text-[var(--color-text-secondary)]">
                  {t('verify.no_code')}
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
                    ? `${t('verify.resend')} (${formatTime(countdown)})`
                    : t('verify.resend')}
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
                {t('verify.next')}
              </Button>
            </div>

            {/* 이메일 재입력 링크 */}
            <Link
              to="/signup"
              className="text-[14px] font-medium text-[var(--color-text-secondary)] hover:text-[var(--color-link-blue)] transition-colors"
            >
              {t('verify.change_email')}
            </Link>

          </div>
        </div>
      </div>
    </div>
  );
}

export default VerifyEmailPage;
