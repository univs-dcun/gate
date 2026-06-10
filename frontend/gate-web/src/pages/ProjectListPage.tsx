/**
 * ProjectListPage — 프로젝트 리스트
 *
 * Figma node 197-6539 기반
 * API: GET /v1/projects
 */

import { Fragment, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { TopNav } from '@/components/layout';
import { CreateProjectModal, ProjectCreatedPanel, Pagination } from '@/components/common';
import type { CreateProjectData } from '@/components/common';
import { FaceIdIcon, PlamIcon } from '@/components/ui/icons';
import axios from 'axios';
import { getProjects, createProject, updatePackageKey } from '@/services/project';
import type { Project } from '@/services/project';
import { useProjectContext } from '@/contexts/ProjectContext';
import { setCurrentApiKey } from '@/services/http';

/* 데모 접속 베이스 URL (DemoQRCard와 동일 규칙) */
const MOBILE_BASE = window.__APP_CONFIG__?.mobileBaseUrl ?? import.meta.env.VITE_MOBILE_BASE_URL ?? 'https://develop.univs.ai:7778';

/* ── 아이콘 ── */
const AddIcon = () => (
  <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" aria-hidden="true">
    <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
  </svg>
);


const SearchIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#64748B" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
  </svg>
);

// const SortIcon = () => (
//   <svg width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true">
//     <path d="M10 4l-4 6h8L10 4z" fill="#94A3B8" />
//     <path d="M10 16l-4-6h8L10 16z" fill="#94A3B8" />
//   </svg>
// );


/* ── 스타일 상수 ── */
const TH      = 'px-3 py-3 text-center text-[16px] font-semibold text-[#475569] tracking-[-0.4px] leading-[1.4] whitespace-nowrap';
const TH_LEFT = 'px-3 py-3 text-left   text-[16px] font-semibold text-[#475569] tracking-[-0.4px] leading-[1.4] whitespace-nowrap';

/* ── 서브컴포넌트 ── */
/* 프로젝트별 랜덤(결정적) 이미지 — projectId 기반 그라데이션 아바타 */
const AVATAR_PALETTE: [string, string][] = [
  ['#60A5FA', '#A78BFA'], ['#34D399', '#22D3EE'], ['#FBBF24', '#F472B6'],
  ['#F87171', '#FB923C'], ['#818CF8', '#C084FC'], ['#2DD4BF', '#60A5FA'],
];
function ProjectAvatar({ id }: { id: number }) {
  const [c1, c2] = AVATAR_PALETTE[Math.abs(id) % AVATAR_PALETTE.length];
  return (
    <span
      className="flex-shrink-0 w-[18px] h-[18px] rounded-[4px]"
      style={{ background: `linear-gradient(135deg, ${c1}, ${c2})` }}
      aria-hidden
    />
  );
}

/* 카운트 셀 — tone별 숫자 색상 (Figma: 합계 #334155 / 얼굴 primary-700 / 손바닥 purple-400)
   value=null → '–' (해당 모달리티 데이터 없음) */
const COUNT_TONE: Record<'total' | 'face' | 'palm', string> = {
  total: 'var(--color-neutral-700)',     /* #334155 */
  face:  'var(--color-primary-700)',     /* #004399 */
  palm:  'var(--color-purple-400)',      /* #8A58FF */
};
function CountCell({ value, tone, divider }: { value: number | null; tone: 'total' | 'face' | 'palm'; divider?: boolean }) {
  const { t } = useTranslation();
  return (
    <td className={['px-3 text-right align-middle', divider ? 'border-r border-[var(--color-neutral-300)]' : ''].join(' ')}>
      {value === null ? (
        <span className="text-[15px] font-semibold" style={{ color: COUNT_TONE[tone] }}>-</span>
      ) : (
        <span className="inline-flex items-baseline gap-1 whitespace-nowrap">
          <span className="text-[15px] font-semibold leading-[20px]" style={{ color: COUNT_TONE[tone] }}>
            {value.toLocaleString('ko-KR')}
          </span>
          <span className="text-[13px] font-normal text-[var(--color-neutral-500)] leading-[18px]">{t('common.count_unit')}</span>
        </span>
      )}
    </td>
  );
}

/* 인증 방식 셀 — 얼굴=블루(#006fff) / 손바닥=퍼플(#8a58ff): 좌측 컬러 바(2x20) + 아이콘 + 라벨 */
function AuthMethodCell({ moduleType }: { moduleType?: 'FACE' | 'PALM' }) {
  const { t } = useTranslation();
  const isPalm = moduleType === 'PALM';
  const color = isPalm ? 'var(--color-purple-400)' : 'var(--color-link-blue)';
  return (
    <div className="flex items-center gap-2">
      <span className="w-[2px] h-5 rounded-[4px] flex-shrink-0" style={{ backgroundColor: color }} />
      {isPalm ? <PlamIcon size={20} /> : <FaceIdIcon size={20} />}
      <span className="text-[14px] font-semibold tracking-[-0.35px] leading-[20px] whitespace-nowrap" style={{ color }}>
        {isPalm ? t('auth_type.palm_short') : t('auth_type.face_short')}
      </span>
    </div>
  );
}



/* ── 메인 컴포넌트 ── */
export default function ProjectListPage() {
  const navigate    = useNavigate();
  const { t }       = useTranslation();
  const queryClient = useQueryClient();
  const { setSelectedId } = useProjectContext();

  const [pageNum,      setPageNum]      = useState(1);
  const [pageSize,     setPageSize]     = useState(10);
  const [searchInput,  setSearchInput]  = useState('');
  const [searchQuery,  setSearchQuery]  = useState('');
  const [isModalOpen,    setIsModalOpen]    = useState(false);
  const [isSubmitting,   setIsSubmitting]   = useState(false);
  const [createError,    setCreateError]    = useState<string | null>(null);
  const [createdProject, setCreatedProject] = useState<Project | null>(null);

  const applySearch = () => { setSearchQuery(searchInput.trim()); setPageNum(1); };

  const { data, isLoading } = useQuery({
    queryKey: ['projects', pageNum, pageSize, searchQuery],
    queryFn:  async () => {
      const params: Parameters<typeof getProjects>[0] = { page: pageNum, pageSize };
      if (searchQuery) params.projectKeyword = searchQuery;
      const res = await getProjects(params);
      return res.data.data;
    },
  });

  const projects    = data?.contents ?? [];
  const totalPages  = data?.page.totalPages ?? 1;
  const totalCount  = data?.page.totalCount ?? 0;
  const MAX_PROJECTS = 10;
  const isAtLimit   = totalCount >= MAX_PROJECTS;

  const handleRowClick = (project: Project) => {
    // ProjectContext의 ['projects'] 캐시가 없으면 현재 페이지 데이터로 미리 채움
    // → 대시보드 진입 즉시 사이드바에 선택된 프로젝트가 표시됨
    if (!queryClient.getQueryData(['projects'])) {
      queryClient.setQueryData(['projects'], projects);
    }
    setSelectedId(String(project.projectId));
    setCurrentApiKey(project.apiKey); // React 배치 업데이트 전 즉시 동기화
    navigate('/dashboard');
  };

  const handleCreateProject = async (formData: CreateProjectData) => {
    setIsSubmitting(true);
    setCreateError(null);
    try {
      const res = await createProject({
        projectName:        formData.name,
        projectDescription: formData.goal,
        projectType:        formData.projectType,
        projectModuleType:  formData.projectModuleType,
      });
      if (formData.projectType === 'EXTERNAL' && formData.packageKey) {
        await updatePackageKey(res.data.data.projectId, formData.packageKey);
      }
      await queryClient.invalidateQueries({ queryKey: ['projects'] });
      setIsModalOpen(false);
      setCreatedProject(res.data.data);
    } catch (err) {
      const msg =
        axios.isAxiosError(err) && err.response?.data?.errors?.message
          ? err.response.data.errors.message
          : t('projects.create_error_generic');
      setCreateError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="flex flex-col h-screen overflow-hidden bg-[var(--color-bg-page)] min-w-[1280px]">
      <TopNav minimal />
      <main className="flex-1 overflow-y-auto bg-white">
      <div className="mx-auto w-full max-w-[1600px] px-8 pb-16">
        {/* 페이지 타이틀 */}
        <h1 className="pt-10 text-[26px] font-semibold text-[var(--color-neutral-800)] tracking-[-0.65px] leading-[1.4]">
          {t('projects.list')}
        </h1>

        {/* 필터 바 */}
        <div className="flex items-center justify-between mt-[30px] gap-3">
          <div className="flex items-center gap-3">
            {/* TODO: 백엔드 구현 후 활성화 */}
            {/* <FilterSelect icon={<ShieldIcon />} label={t('auth_type.method')} /> */}
            {/* <FilterSelect icon={<PlanIcon size={20} />} label={t('projects.plan')} /> */}

            {/* 검색 */}
            <div className={[
              'flex items-center gap-2 h-[46px] px-3 rounded-[8px] min-w-[200px]',
              'bg-white border border-[#CBD5E1]',
              'hover:border-[var(--color-border-focus)] transition-colors',
            ].join(' ')}>
              <SearchIcon />
              <input
                type="text"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') applySearch(); }}
                placeholder={t('projects.search_placeholder')}
                className="flex-1 text-[14px] font-medium text-[#1E293B] tracking-[-0.35px] placeholder:text-[#94A3B8] bg-transparent focus:outline-none"
              />
            </div>
          </div>

          {/* 오른쪽: 프로젝트 수 안내 + 추가 버튼 */}
          <div className="flex items-center gap-5 flex-shrink-0">

            {/* 프로젝트 수 / 최대 안내 */}
            <div className="flex flex-col items-end gap-0.5">
              <span className={[
                'font-bold text-[#1E293B] tracking-[-0.375px] leading-[1.4]',
                isAtLimit ? 'text-[13px]' : 'text-[15px]',
              ].join(' ')}>
                {t('projects.count_label', { count: totalCount, max: MAX_PROJECTS })}
              </span>
              <span className={[
                'font-normal text-[#94A3B8] tracking-[-0.325px] leading-[1.4]',
                isAtLimit ? 'text-[11px]' : 'text-[13px]',
              ].join(' ')}>
                {t('projects.max_hint', { max: MAX_PROJECTS })}
              </span>
            </div>

            {/* 추가 버튼 */}
            <button
              type="button"
              onClick={() => setIsModalOpen(true)}
              disabled={isAtLimit}
              className={[
                'flex items-center gap-2 h-[46px] pl-2 pr-4 rounded-[8px]',
                isAtLimit
                  ? 'bg-[#CBD5E1] cursor-not-allowed'
                  : 'bg-[#1A71F6] hover:bg-[var(--color-link-blue)] transition-colors',
                'text-[15px] font-medium text-white tracking-[-0.375px]',
              ].join(' ')}
            >
              <AddIcon />
              <span>{t('projects.add_btn')}</span>
            </button>

          </div>
        </div>

        {/* 테이블 */}
        <div className="mt-[26px] w-full overflow-x-auto">
          <table className="w-full table-fixed border-collapse">
            <colgroup>
              <col style={{ width: '247px' }} />
              <col /><col /><col /><col /><col /><col /><col />
            </colgroup>
            <thead>
              <tr className="border-b border-[var(--color-neutral-300)]">
                <th className={TH_LEFT}>{t('projects.name_label')}</th>
                <th className={TH_LEFT}>{t('projects.col_auth_method')}</th>
                <th className={TH}>{t('module.enrollment')}</th>
                <th className={TH}>{t('module.verification')}</th>
                <th className={TH}>{t('module.verify_image')}</th>
                <th className={TH}>{t('module.matching')}</th>
                <th className={TH}>{t('module.liveness')}</th>
                <th className={TH}>{t('projects.col_created_at')}</th>
              </tr>
            </thead>
            <tbody>
              {isLoading && (
                <tr>
                  <td colSpan={8} className="px-[10px] py-8 text-center text-[14px] text-[var(--color-neutral-400)]">
                    {t('common.loading')}
                  </td>
                </tr>
              )}
              {!isLoading && projects.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-[10px] py-8 text-center text-[14px] text-[var(--color-neutral-400)]">
                    {t('common.no_data')}
                  </td>
                </tr>
              )}
              {projects.map((project) => {
                const created = project.createdAt ? project.createdAt.slice(0, 16).replace('T', ' ') : '-';
                /* 현재 카운트는 해당 프로젝트의 단일 인증방식 카운트 → 모듈타입 행에만 적용, 반대 방식은 '-' */
                const mod = project.projectModuleType ?? 'FACE';
                const c = {
                  reg:  project.countUserRegistration ?? 0,
                  vid:  project.countVerifyById ?? 0,
                  vimg: project.countVerifyByImage ?? 0,
                  iden: project.countIdentify ?? 0,
                  live: project.countLiveness ?? 0,
                };
                const face = mod === 'FACE' ? c : null;
                const palm = mod === 'PALM' ? c : null;
                return (
                  <Fragment key={project.projectId}>
                    {/* 합계(프로젝트 헤더) 행 — 배경 강조, 구분선 없음 */}
                    <tr onClick={() => handleRowClick(project)} className="h-[54px] bg-[var(--color-neutral-50-bg)] hover:bg-[var(--color-neutral-100)] cursor-pointer transition-colors">
                      <td className="px-3 align-middle">
                        <div className="flex items-center gap-2">
                          <ProjectAvatar id={project.projectId} />
                          <span className="text-[15px] font-semibold text-[var(--color-neutral-700)] leading-[20px] truncate">
                            {project.projectName}
                          </span>
                        </div>
                      </td>
                      <td className="px-3" />
                      <CountCell value={c.reg}  tone="total" />
                      <CountCell value={c.vid}  tone="total" />
                      <CountCell value={c.vimg} tone="total" />
                      <CountCell value={c.iden} tone="total" />
                      <CountCell value={c.live} tone="total" />
                      <td className="px-3 text-center align-middle">
                        <span className="text-[15px] font-semibold text-[var(--color-neutral-700)] leading-[20px] whitespace-nowrap">
                          {created}
                        </span>
                      </td>
                    </tr>

                    {/* 얼굴 행 */}
                    <tr onClick={() => handleRowClick(project)} className="h-[42px] hover:bg-[#f8fafc] cursor-pointer transition-colors">
                      <td className="px-3" />
                      <td className="px-3 align-middle border-r border-[var(--color-neutral-300)]"><AuthMethodCell moduleType="FACE" /></td>
                      <CountCell value={face?.reg  ?? null} tone="face" divider />
                      <CountCell value={face?.vid  ?? null} tone="face" divider />
                      <CountCell value={face?.vimg ?? null} tone="face" divider />
                      <CountCell value={face?.iden ?? null} tone="face" divider />
                      <CountCell value={face?.live ?? null} tone="face" divider />
                      <td className="px-3" />
                    </tr>

                    {/* 손바닥 행 — 프로젝트 그룹 하단 구분선 */}
                    <tr onClick={() => handleRowClick(project)} className="h-[42px] border-b border-[var(--color-neutral-300)] hover:bg-[#f8fafc] cursor-pointer transition-colors">
                      <td className="px-3" />
                      <td className="px-3 align-middle border-r border-[var(--color-neutral-300)]"><AuthMethodCell moduleType="PALM" /></td>
                      <CountCell value={palm?.reg  ?? null} tone="palm" divider />
                      <CountCell value={palm?.vid  ?? null} tone="palm" divider />
                      <CountCell value={palm?.vimg ?? null} tone="palm" divider />
                      <CountCell value={palm?.iden ?? null} tone="palm" divider />
                      <CountCell value={palm?.live ?? null} tone="palm" divider />
                      <td className="px-3" />
                    </tr>
                  </Fragment>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* 페이지네이션 */}
        <Pagination
          pageNum={pageNum}
          pageSize={pageSize}
          totalPages={totalPages}
          totalElements={data?.page.totalElements ?? 0}
          totalRecords={data?.page.totalCount}
          currentCount={projects.length}
          onPageChange={setPageNum}
          onPageSizeChange={(size) => { setPageSize(size); setPageNum(1); }}
        />

      </div>
      </main>

      <CreateProjectModal
        isOpen={isModalOpen}
        onClose={() => { setIsModalOpen(false); setCreateError(null); }}
        onSubmit={handleCreateProject}
        loading={isSubmitting}
        error={createError}
      />

      {createdProject && (
        <ProjectCreatedPanel
          projectName={createdProject.projectName}
          purpose={createdProject.projectDescription}
          apiKey={createdProject.apiKey ?? ''}
          demoUrl={`${MOBILE_BASE}?apiKey=${createdProject.apiKey ?? ''}`}
          onGoDashboard={() => { setCreatedProject(null); navigate('/dashboard'); }}
        />
      )}
    </div>
  );
}
