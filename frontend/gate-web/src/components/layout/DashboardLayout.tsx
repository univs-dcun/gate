import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import TopNav from './TopNav';
import { useProjectContext } from '@/contexts/ProjectContext';
import { getProjectSettings } from '@/services/project';

interface DashboardLayoutProps {
  children: ReactNode;
}

/**
 * 대시보드 레이아웃 — 상단 GNB(TopNav) + 콘텐츠 영역
 *
 * Figma node 1722:3885 기반. 기존 좌측 Sidebar → 상단 GNB로 전환.
 *  - 콘텐츠: max-w 1600 / 페이지 min-w 1280 (mx-auto 중앙 정렬)
 *  - 개인정보 동의 상태는 GNB 뱃지로 표시 (consentEnabled 주입)
 */
function DashboardLayout({ children }: DashboardLayoutProps) {
  const { selectedProject } = useProjectContext();
  const projectId = selectedProject?.id ? Number(selectedProject.id) : undefined;

  const { data: settings } = useQuery({
    queryKey:  ['project-settings', projectId],
    queryFn:   () => getProjectSettings(projectId!).then(r => r.data.data),
    enabled:   !!projectId,
    staleTime: 0,
  });

  const consentEnabled = settings?.consentEnabled ?? false;

  return (
    <div className="flex flex-col h-screen overflow-hidden bg-[var(--color-bg-page)] min-w-[1280px]">
      <TopNav consented={consentEnabled} />

      <main className="flex-1 overflow-y-auto">
        <div className="mx-auto w-full max-w-[1600px] px-8 py-6">
          {children}
        </div>
      </main>
    </div>
  );
}

export default DashboardLayout;
