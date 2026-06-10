import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Input, Button, Checkbox } from '@/components/ui';
import { Header } from '@/components/layout';
import { sendEmailCode } from '@/services/auth';

/* ── 아이콘 ─────────────────────────────────────────────── */
const EmailIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
    <polyline points="22,6 12,13 2,6" />
  </svg>
);

/* 채워진 원형 느낌표 아이콘 (약관 오류) */
const AlertCircleSolidIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" className="flex-shrink-0">
    <circle cx="12" cy="12" r="10" />
    <path d="M12 8v4" stroke="white" strokeWidth="2.5" strokeLinecap="round" />
    <path d="M12 16h.01" stroke="white" strokeWidth="2.5" strokeLinecap="round" />
  </svg>
);

/* ── 이메일 유효성 ──────────────────────────────────────── */
const isValidEmail = (v: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);

/* ── 컴포넌트 ───────────────────────────────────────────── */
function SignupPage() {
  const { t }    = useTranslation();
  const navigate = useNavigate();

  const [email, setEmail]                       = useState('');
  const [emailTouched, setEmailTouched]         = useState(false);
  const [ageConsent, setAgeConsent]             = useState(false);
  const [termsConsent, setTermsConsent]         = useState(false);
  const [privacyConsent, setPrivacyConsent]     = useState(false);
  const [marketingConsent, setMarketingConsent] = useState(false);
  const [showTermsError, setShowTermsError]     = useState(false);
  const [loading, setLoading]                   = useState(false);
  const [serverError, setServerError]           = useState('');

  /* ── 파생 상태 ── */
  const emailValid          = isValidEmail(email);
  const requiredChecked     = ageConsent && termsConsent && privacyConsent;
  const isAllChecked        = requiredChecked && marketingConsent;
  const canSubmit           = emailValid && requiredChecked;
  const showEmailError      = emailTouched && email.length > 0 && !emailValid;
  /* 약관 에러: showTermsError 플래그 AND 아직 미충족 상태 */
  const showTermsErrorMsg   = showTermsError && !requiredChecked;

  /* ── 핸들러 ── */
  const handleAgreeAll = (checked: boolean) => {
    setAgeConsent(checked);
    setTermsConsent(checked);
    setPrivacyConsent(checked);
    setMarketingConsent(checked);
    if (checked) setShowTermsError(false);
  };

  const handleRequiredChange = (
    setter: (v: boolean) => void,
    checked: boolean,
    others: boolean[],
  ) => {
    setter(checked);
    /* 이 항목이 체크되어 나머지도 전부 체크된 상태라면 에러 해제 */
    if (checked && others.every(Boolean)) setShowTermsError(false);
  };

  const handleSubmit = async () => {
    if (loading) return;
    setLoading(true);
    setServerError('');
    try {
      await sendEmailCode(email);
      navigate('/verify-email', { state: { email } });
    } catch (err: unknown) {
      const code = (err as { response?: { data?: { errors?: { code?: string } } } })?.response?.data?.errors?.code;
      if (code === 'AUTH-101') {
        setServerError(t('signup.error_email_in_use'));
      }
      setLoading(false);
    }
  };

  /**
   * 버튼이 disabled 상태일 때도 wrapper div 클릭으로 에러 힌트 표시
   * (disabled button 은 onClick 이벤트를 막지만 부모 div 는 받음)
   */
  const handleWrapperClick = () => {
    if (canSubmit) return; // 활성 상태면 Button 자체 onClick이 처리
    setEmailTouched(true);
    if (emailValid && !requiredChecked) setShowTermsError(true);
  };

  return (
    <div className="min-h-screen bg-white flex flex-col">
      <Header hideAuthButtons />

      <div
        className="flex-1 flex justify-center px-4"
        style={{ paddingTop: 'calc(var(--header-height) + 140px)' }}
      >
        <div className="w-full max-w-[480px] flex flex-col gap-10 pb-20">

          {/* 타이틀 */}
          <div className="flex flex-col gap-2 text-center">
            <h1 className="text-[36px] font-semibold text-[var(--color-neutral-800)] tracking-[-0.9px] leading-[1.4]">
              {t('signup.title')}
            </h1>
            <p className="text-[14px] font-medium text-[var(--color-text-secondary)] tracking-[-0.35px] leading-[1.4] whitespace-pre-line">
              {t('signup.subtitle')}
            </p>
          </div>

          {/* 폼 */}
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-8">

              {/* 이메일 입력 + 에러 메시지 */}
              <div className="flex flex-col gap-1.5">
                <Input
                  id="signup-email"
                  type="email"
                  label={t('auth.email')}
                  value={email}
                  onChange={(v) => { setEmail(v); setServerError(''); }}
                  onBlur={() => { if (email.length > 0) setEmailTouched(true); }}
                  placeholder={t('auth.email_placeholder')}
                  leftIcon={<EmailIcon />}
                  error={showEmailError}
                  autoComplete="email"
                />
                {showEmailError && (
                  <p className="text-[13px] font-medium text-[var(--color-entry)] tracking-[-0.35px] leading-[1.4]">
                    {t('signup.email_invalid')}
                  </p>
                )}
              </div>

              {/* 약관 동의 그룹 + 에러 메시지 */}
              <div className="flex flex-col gap-2">
                <div className="flex flex-col gap-4 border border-[#e8eef2] rounded-[8px] p-4">

                  {/* 전체 동의 */}
                  <Checkbox
                    checked={isAllChecked}
                    onChange={(e) => handleAgreeAll(e.target.checked)}
                    label={t('signup.agree_all')}
                  />

                  {/* 구분선 */}
                  <div className="-mx-4 border-t border-[#e8eef2]" />

                  {/* 개별 항목 */}
                  <Checkbox
                    checked={ageConsent}
                    onChange={(e) => handleRequiredChange(setAgeConsent, e.target.checked, [termsConsent, privacyConsent])}
                    label={t('signup.age_consent')}
                  />
                  <Checkbox
                    checked={termsConsent}
                    onChange={(e) => handleRequiredChange(setTermsConsent, e.target.checked, [ageConsent, privacyConsent])}
                    label={t('signup.terms_consent')}
                  />
                  <Checkbox
                    checked={privacyConsent}
                    onChange={(e) => handleRequiredChange(setPrivacyConsent, e.target.checked, [ageConsent, termsConsent])}
                    label={t('signup.privacy_consent')}
                  />
                  <Checkbox
                    checked={marketingConsent}
                    onChange={(e) => setMarketingConsent(e.target.checked)}
                    label={t('signup.marketing_consent')}
                  />
                </div>

                {/* 약관 에러 메시지 */}
                {showTermsErrorMsg && (
                  <div className="flex items-center gap-1.5 text-[var(--color-entry)]">
                    <AlertCircleSolidIcon />
                    <p className="text-[13px] font-medium tracking-[-0.35px] leading-[1.4]">
                      {t('signup.terms_required')}
                    </p>
                  </div>
                )}
              </div>
            </div>

            {/* 제출 버튼 — wrapper div 로 disabled 상태 클릭도 감지 */}
            <div onClick={handleWrapperClick}>
              <Button
                type="button"
                variant="primary"
                size="lg"
                fullWidth
                loading={loading}
                disabled={!canSubmit}
                onClick={handleSubmit}
              >
                {t('signup.send_code')}
              </Button>
            </div>

            {/* 서버 에러 메시지 */}
            {serverError && (
              <div className="flex items-center gap-1.5 text-[var(--color-entry)]">
                <AlertCircleSolidIcon />
                <p className="text-[13px] font-medium tracking-[-0.35px] leading-[1.4]">
                  {serverError}
                </p>
              </div>
            )}
          </div>

          {/* 로그인 링크 */}
          <div className="flex items-center justify-center gap-1 text-[16px]">
            <span className="font-medium text-[var(--color-text-secondary)]">
              {t('signup.has_account')}
            </span>
            <Link
              to="/login"
              className="font-semibold text-[var(--color-link-blue)] hover:text-[var(--color-link-blue-hover)] transition-colors"
            >
              {t('auth.login')}
            </Link>
          </div>

        </div>
      </div>
    </div>
  );
}

export default SignupPage;
