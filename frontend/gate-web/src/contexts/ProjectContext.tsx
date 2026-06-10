import { createContext, useContext, useState } from 'react';
import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getProjects } from '@/services/project';
import type { Project } from '@/services/project';
import type { ProjectOption } from '@/components/ui';
import { setCurrentApiKey } from '@/services/http';

const STORAGE_KEY     = 'selected_project_id';
const API_KEY_STORAGE = 'selected_api_key';

interface ProjectContextValue {
  projects:        ProjectOption[];
  selectedId:      string;
  setSelectedId:   (id: string) => void;
  selectedProject: ProjectOption | undefined;
  isLoading:       boolean;
}

const ProjectContext = createContext<ProjectContextValue | null>(null);

export function ProjectProvider({ children }: { children: ReactNode }) {
  const [selectedId, setSelectedIdState] = useState(() => {
    /* projects 쿼리 완료 전 API 호출 시 X-Api-Key 누락 방지:
     * 초기 렌더 시점에 캐시된 apiKey를 즉시 복원 */
    const savedApiKey = localStorage.getItem(API_KEY_STORAGE);
    if (savedApiKey) setCurrentApiKey(savedApiKey);
    return localStorage.getItem(STORAGE_KEY) ?? '';
  });

  const setSelectedId = (id: string) => {
    localStorage.setItem(STORAGE_KEY, id);
    setSelectedIdState(id);
  };

  const { data, isLoading } = useQuery({
    queryKey: ['projects'],
    queryFn: async () => {
      const res = await getProjects({ page: 1, pageSize: 50 });
      return res.data.data.contents;
    },
    enabled: !!localStorage.getItem('access_token'),
    retry: false, // httpClient 인터셉터가 401 재시도 처리
  });

  const projects: ProjectOption[] = (data ?? []).map((p: Project) => ({
    id:                String(p.projectId),
    name:              p.projectName,
    authType:          'face',
    apiKey:            p.apiKey ?? '',
    projectType:       p.projectType,
    projectModuleType: p.projectModuleType,
    description:       p.projectDescription,
  }));

  /* 저장된 ID가 실제 프로젝트 목록에 있으면 유지, 없으면 첫 번째로 폴백 */
  const savedExists = projects.length > 0 && projects.some((p) => p.id === selectedId);
  const effectiveId = savedExists ? selectedId : projects[0]?.id ?? '';
  const selectedProject = projects.find((p) => p.id === effectiveId);

  /* 선택된 프로젝트의 API 키를 인터셉터 및 localStorage에 동기화
   * - useEffect 대신 렌더 중 직접 호출 (race condition 방지)
   * - 로딩 중에는 캐시된 키를 유지 — 쿼리 완료 전 X-Api-Key 누락 방지 */
  if (selectedProject?.apiKey) {
    setCurrentApiKey(selectedProject.apiKey);
    localStorage.setItem(API_KEY_STORAGE, selectedProject.apiKey);
  } else if (!isLoading) {
    setCurrentApiKey(null);
    localStorage.removeItem(API_KEY_STORAGE);
  }

  return (
    <ProjectContext.Provider
      value={{ projects, selectedId: effectiveId, setSelectedId, selectedProject, isLoading }}
    >
      {children}
    </ProjectContext.Provider>
  );
}

export function useProjectContext() {
  const ctx = useContext(ProjectContext);
  if (!ctx) throw new Error('useProjectContext must be used within ProjectProvider');
  return ctx;
}
