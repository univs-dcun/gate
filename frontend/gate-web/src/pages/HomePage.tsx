import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getProjects } from '@/services/project';

/**
 * / 루트 진입점 — 인증 상태에 따라 적절한 페이지로 리다이렉트
 *
 * - 토큰 없음 → /login
 * - 토큰 있음 + 프로젝트 0개 → /welcome
 * - 토큰 있음 + 프로젝트 1개 → /dashboard
 * - 토큰 있음 + 프로젝트 2개 이상 → /projects
 */
function HomePage() {
  const navigate = useNavigate();
  const { t } = useTranslation();

  useEffect(() => {
    const token = localStorage.getItem('access_token');

    if (!token) {
      navigate('/login', { replace: true });
      return;
    }

    getProjects({ page: 1, pageSize: 50 })
      .then((res) => {
        const activeCount = res.data.data.contents.filter((p) => p.status === 'ACTIVE').length;
        if (activeCount === 0) {
          navigate('/welcome', { replace: true });
        } else if (activeCount === 1) {
          navigate('/dashboard', { replace: true });
        } else {
          navigate('/projects', { replace: true });
        }
      })
      .catch(() => {
        /* 토큰 만료 또는 네트워크 오류 → http 인터셉터가 /login 리다이렉트 처리 */
        navigate('/login', { replace: true });
      });
  }, [navigate]);

  /* 리다이렉트 전 로딩 표시 */
  return (
    <div className="min-h-screen flex items-center justify-center bg-[var(--color-bg-page)]">
      <div className="flex flex-col items-center gap-3">
        <div
          className="w-8 h-8 rounded-full border-2 border-[var(--color-link-blue)] border-t-transparent animate-spin"
        />
        <span className="text-sm text-[var(--color-text-secondary)]">{t('common.loading')}</span>
      </div>
    </div>
  );
}

export default HomePage;
