import { useState, useEffect } from 'react';
import type { FormEvent } from 'react';
import { useNavigate, Link, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { Button, Checkbox, Input } from '@/components/ui';
import { Header } from '@/components/layout';
import { login } from '@/services/auth';
import { getProjects } from '@/services/project';

/* ─── 아이콘 ─────────────────────────────────────────────── */
const UserIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
    <circle cx="12" cy="7" r="4" />
  </svg>
);
const LockIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
    <path d="M7 11V7a5 5 0 0 1 10 0v4" />
  </svg>
);
const AlertCircleIcon = () => (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10" />
    <line x1="12" y1="8" x2="12" y2="12" />
    <line x1="12" y1="16" x2="12.01" y2="16" />
  </svg>
);

/* ── 성공 토스트 아이콘 ── */
const CheckCircleIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="flex-shrink-0">
    <circle cx="12" cy="12" r="10" />
    <path d="m9 12 2 2 4-4" />
  </svg>
);

function LoginPage() {
  const navigate     = useNavigate();
  const { t }        = useTranslation();
  const queryClient  = useQueryClient();
  const location     = useLocation();
  const [email, setEmail]           = useState('');
  const [password, setPassword]     = useState('');
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError]           = useState('');
  const [loading, setLoading]       = useState(false);
  const [toast, setToast]           = useState('');

  /* 비밀번호 변경 완료 토스트 */
  useEffect(() => {
    const state = location.state as { toast?: string } | null;
    if (state?.toast === 'password_changed') {
      setToast(t('forgot_password.toast_success'));
      window.history.replaceState({}, '');
      const id = setTimeout(() => setToast(''), 3000);
      return () => clearTimeout(id);
    }
  }, [location.state, t]);

  const isFormFilled = email.trim().length > 0 && password.trim().length > 0;
  const hasError = error.length > 0;

  const handleEmailChange    = (v: string) => { setEmail(v);    setError(''); };
  const handlePasswordChange = (v: string) => { setPassword(v); setError(''); };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!isFormFilled || loading) return;
    setLoading(true);
    setError('');
    try {
      /* 1. 로그인 */
      const loginRes = await login(email, password);
      const { accessToken, refreshToken, accountResponseDTO } = loginRes.data.data;
      localStorage.setItem('access_token', accessToken);
      localStorage.setItem('refresh_token', refreshToken);
      localStorage.setItem('user_email', accountResponseDTO.email);
      localStorage.setItem('account_id', String(accountResponseDTO.accountId));
      if (rememberMe) localStorage.setItem('remembered_email', email);
      else            localStorage.removeItem('remembered_email');

      /* 2. 프로젝트 목록 조회 → 캐시에 즉시 반영 (navigate 후 ProjectContext 로딩 없이 바로 사용 가능) */
      const projRes = await getProjects({ page: 1, pageSize: 50 });
      const contents = projRes.data.data.contents;
      queryClient.setQueryData(['projects'], contents);
      const activeCount = contents.filter((p) => p.status === 'ACTIVE').length;
      if (activeCount === 0) {
        navigate('/welcome');
      } else if (activeCount === 1) {
        navigate('/dashboard');
      } else {
        navigate('/projects');
      }
    } catch (err) {
      const code = (err as { response?: { data?: { errors?: { code?: string } } } })
        ?.response?.data?.errors?.code;
      if (code === 'AUTH-110') {
        setError(t('auth.error_message'));          // 비밀번호 불일치
      } else if (code === 'AUTH-112') {
        setError(t('auth.error_message'));          // 계정 없음
      } else if (code === 'AUTH-111') {
        setError(t('auth.error_locked'));           // 계정 잠김
      } else {
        setError(t('auth.error_message'));
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      className="min-h-screen flex flex-col"
      style={{ background: 'radial-gradient(ellipse 100% 55% at 50% 0%, #dbeafe 0%, #eff6ff 40%, #ffffff 70%)' }}
    >
      {/* 비밀번호 변경 완료 토스트 */}
      {toast && (
        <div className={[
          'fixed top-[calc(var(--header-height)+12px)] left-1/2 -translate-x-1/2 z-50',
          'flex items-center gap-2.5 px-5 py-3.5 rounded-[10px]',
          'bg-[var(--color-neutral-800)] text-white shadow-lg',
          'text-[14px] font-medium tracking-[-0.35px] leading-[1.4]',
          'animate-[fadeInDown_0.25s_ease-out]',
        ].join(' ')}>
          <CheckCircleIcon />
          {toast}
        </div>
      )}
      <Header hideAuthButtons />
      <div
        className="flex-1 flex items-center justify-center px-4 py-8"
        style={{ paddingTop: 'calc(var(--header-height) + 2rem)' }}
      >
        <div className="w-full max-w-[420px]">
          <div
            className="bg-white rounded-2xl px-10 py-10"
            style={{ boxShadow: '0 4px 32px rgba(0, 0, 0, 0.10), 0 1px 4px rgba(0,0,0,0.06)' }}
          >
            <div className="mb-7 text-center">
              <h1 className="text-2xl font-bold text-[var(--color-text-primary)] mb-1.5">
                {t('auth.login')}
              </h1>
              <p className="text-sm text-[var(--color-text-secondary)]">
                {t('auth.welcome')}
              </p>
            </div>

            <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
              <Input
                id="email"
                type="email"
                label={t('auth.email')}
                value={email}
                onChange={handleEmailChange}
                placeholder={t('auth.email_placeholder')}
                leftIcon={<UserIcon />}
                error={hasError}
                autoComplete="email"
                disabled={loading}
              />
              <Input
                id="password"
                type="password"
                label={t('auth.password')}
                value={password}
                onChange={handlePasswordChange}
                placeholder={t('auth.password_placeholder')}
                leftIcon={<LockIcon />}
                error={hasError}
                autoComplete="current-password"
                disabled={loading}
              />

              <div className="flex items-center justify-between">
                <Checkbox
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                  label={t('auth.remember_me')}
                  disabled={loading}
                />
                <Link
                  to="/forgot-password"
                  className="text-xs text-[var(--color-text-secondary)] hover:text-[var(--color-link-blue)] transition-colors underline-offset-2 hover:underline"
                >
                  {t('auth.forgot_password')}
                </Link>
              </div>

              {hasError && (
                <div className="flex items-center gap-2 px-3 py-2.5 rounded-[var(--radius-md)] bg-[var(--color-entry-bg)] text-[var(--color-entry)]">
                  <AlertCircleIcon />
                  <span className="text-xs font-medium">{error}</span>
                </div>
              )}

              <Button
                type="submit"
                variant="primary"
                size="lg"
                fullWidth
                loading={loading}
                disabled={!isFormFilled}
                className="mt-1"
              >
                {t('auth.login')}
              </Button>
            </form>

            <div className="mt-6 pt-5 border-t border-[var(--color-border-default)] flex items-center justify-center gap-1.5 text-sm">
              <span className="text-[var(--color-text-secondary)]">{t('auth.no_account')}</span>
              <Link
                to="/signup"
                className="font-semibold text-[var(--color-link-blue)] hover:text-[var(--color-link-blue-hover)] transition-colors"
              >
                {t('auth.create_account')}
              </Link>
            </div>
          </div>

          <p className="mt-5 text-center text-xs text-[var(--color-text-disabled)]">
            © 2025 UNIVS AI. All rights reserved.
          </p>
        </div>
      </div>
    </div>
  );
}

export default LoginPage;
