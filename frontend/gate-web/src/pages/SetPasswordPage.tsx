import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { Input, Button } from '@/components/ui';
import { Header } from '@/components/layout';
import { signup, login } from '@/services/auth';

/* ── 유효성 ─────────────────────────────────────────────── */
const isValidPassword = (v: string) => v.length >= 8;

/* ── 에러 아이콘 ─────────────────────────────────────────── */
const ErrorCircleIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" className="flex-shrink-0">
    <circle cx="12" cy="12" r="10" />
    <path d="M12 8v4" stroke="white" strokeWidth="2.5" strokeLinecap="round" />
    <path d="M12 16h.01" stroke="white" strokeWidth="2.5" strokeLinecap="round" />
  </svg>
);

/* ── 컴포넌트 ───────────────────────────────────────────── */
function SetPasswordPage() {
  const { t }        = useTranslation();
  const navigate     = useNavigate();
  const location     = useLocation();
  const queryClient  = useQueryClient();
  const email    = (location.state as { email?: string } | null)?.email ?? '';

  const [password, setPassword]               = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordTouched, setPasswordTouched] = useState(false);
  const [confirmTouched, setConfirmTouched]   = useState(false);
  const [loading, setLoading]                 = useState(false);
  const [apiError, setApiError]               = useState('');

  /* 이메일 없이 직접 접근 시 리다이렉트 */
  useEffect(() => {
    if (!email) navigate('/signup', { replace: true });
  }, [email, navigate]);

  /* ── 파생 상태 ── */
  const passwordValid    = isValidPassword(password);
  const confirmValid     = password === confirmPassword;
  const canSubmit        = passwordValid && confirmValid && confirmPassword.length > 0;
  const showPwError      = passwordTouched && password.length > 0 && !passwordValid;
  const showConfirmError = confirmTouched && confirmPassword.length > 0 && !confirmValid;

  /* ── 핸들러 ── */
  const handleSubmit = async () => {
    if (!canSubmit || loading) return;
    setApiError('');
    setLoading(true);
    try {
      await signup(email, password, confirmPassword);
      /* 가입 성공 → 자동 로그인 */
      const loginRes = await login(email, password);
      const { accessToken, refreshToken, accountResponseDTO } = loginRes.data.data;
      localStorage.setItem('access_token', accessToken);
      localStorage.setItem('refresh_token', refreshToken);
      localStorage.setItem('user_email', accountResponseDTO.email);
      localStorage.setItem('account_id', String(accountResponseDTO.accountId));
      await queryClient.invalidateQueries({ queryKey: ['projects'] });
      navigate('/welcome');
    } catch (err) {
      const code = (err as { response?: { data?: { errors?: { code?: string } } } })
        ?.response?.data?.errors?.code;
      if (code === 'AUTH-101') {
        setApiError(t('set_password.error_email_in_use'));
      } else if (code === 'AUTH-123') {
        setApiError(t('set_password.error_not_verified'));
      } else {
        setApiError(t('set_password.error_generic'));
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
        <div className="w-full max-w-[480px] flex flex-col gap-10 pb-20">

          {/* 타이틀 */}
          <div className="flex flex-col gap-2 text-center">
            <h1 className="text-[36px] font-semibold text-[var(--color-neutral-800)] tracking-[-0.9px] leading-[1.4]">
              {t('set_password.title')}
            </h1>
            <p className="text-[14px] font-medium text-[var(--color-text-secondary)] tracking-[-0.35px] leading-[1.4] whitespace-pre-line">
              {t('set_password.subtitle')}
            </p>
          </div>

          {/* 폼 */}
          <div className="flex flex-col gap-8">

            {/* 비밀번호 */}
            <div className="flex flex-col gap-1.5">
              <Input
                id="set-password"
                type="password"
                label={t('set_password.password_label')}
                value={password}
                onChange={(v) => { setPassword(v); setApiError(''); }}
                onBlur={() => { if (password.length > 0) setPasswordTouched(true); }}
                placeholder={t('set_password.password_placeholder')}
                error={showPwError}
                autoComplete="new-password"
              />
              {showPwError && (
                <p className="text-[13px] font-medium text-[var(--color-entry)] tracking-[-0.35px] leading-[1.4]">
                  {t('set_password.password_invalid')}
                </p>
              )}
            </div>

            {/* 비밀번호 확인 */}
            <div className="flex flex-col gap-1.5">
              <Input
                id="confirm-password"
                type="password"
                label={t('set_password.confirm_label')}
                value={confirmPassword}
                onChange={(v) => { setConfirmPassword(v); setApiError(''); }}
                onBlur={() => { if (confirmPassword.length > 0) setConfirmTouched(true); }}
                placeholder={t('set_password.confirm_placeholder')}
                error={showConfirmError}
                autoComplete="new-password"
              />
              {showConfirmError && (
                <p className="text-[13px] font-medium text-[var(--color-entry)] tracking-[-0.35px] leading-[1.4]">
                  {t('set_password.confirm_invalid')}
                </p>
              )}
            </div>

            {/* API 에러 메시지 */}
            {apiError && (
              <div className="flex items-center gap-1.5 text-[#D83232]">
                <ErrorCircleIcon />
                <p className="text-[13px] font-medium tracking-[-0.35px] leading-[1.4]">
                  {apiError}
                </p>
              </div>
            )}

            {/* 제출 버튼 */}
            <Button
              type="button"
              variant="primary"
              size="lg"
              fullWidth
              loading={loading}
              disabled={!canSubmit}
              onClick={handleSubmit}
            >
              {t('set_password.submit')}
            </Button>
          </div>

        </div>
      </div>
    </div>
  );
}

export default SetPasswordPage;
