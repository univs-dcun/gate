/**
 * ProjectWelcomePage — 회원가입 후 첫 화면 (프로젝트 없는 초기 화면)
 *
 * Figma node 1778:3354 기반.
 * 미니멀 GNB + 히어로(뱃지/타이틀/서브타이틀) + 3스텝 가이드 + 첫 프로젝트 생성 CTA
 */

import { Fragment, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation, Trans } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { TopNav } from '@/components/layout';
import { CreateProjectModal, ProjectCreatedPanel } from '@/components/common';
import type { CreateProjectData } from '@/components/common';
import { createProject, updatePackageKey } from '@/services/project';
import type { Project } from '@/services/project';

/* 데모 접속 베이스 URL (DemoQRCard와 동일 규칙) */
const MOBILE_BASE = window.__APP_CONFIG__?.mobileBaseUrl ?? import.meta.env.VITE_MOBILE_BASE_URL ?? 'https://develop.univs.ai:7778';

/* ── 아이콘 ── */
const ChevronRightIcon = () => (
  <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="var(--color-neutral-300)"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <polyline points="9 18 15 12 9 6" />
  </svg>
);
const ArrowRightIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"
    strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <line x1="5" y1="12" x2="19" y2="12" /><polyline points="12 5 19 12 12 19" />
  </svg>
);

/* ── 태그 색상 ── */
const TAG_COLORS: Record<'green' | 'orange' | 'purple', string> = {
  green:  'bg-[var(--color-success-100)] text-[var(--color-success-700)]',
  orange: 'bg-[var(--color-learning-bg)] text-[var(--color-learning)]',
  purple: 'bg-[var(--color-purple-100)] text-[var(--color-purple-400)]',
};

function Tag({ label, color }: { label: string; color: keyof typeof TAG_COLORS }) {
  return (
    <span className={['inline-flex items-center px-3 py-1 rounded-full text-[13px] font-medium whitespace-nowrap', TAG_COLORS[color]].join(' ')}>
      {label}
    </span>
  );
}

/* ── 설명 텍스트 하이라이트 (Trans) — <a>=블루, <b>=다크 강조 ── */
const HIGHLIGHT_COMPONENTS = {
  a: <span className="font-semibold text-[var(--color-link-blue)]" />,
  b: <span className="font-semibold text-[var(--color-neutral-800)]" />,
};

/* ── 단계 정의 ── */
interface StepDef {
  num: number;
  titleKey: string;
  descKey: string;
  tags: { key: string; color: keyof typeof TAG_COLORS }[];
}

const STEPS: StepDef[] = [
  { num: 1, titleKey: 'welcome.step1_title', descKey: 'welcome.step1_desc', tags: [{ key: 'welcome.step1_tag', color: 'green' }] },
  { num: 2, titleKey: 'welcome.step2_title', descKey: 'welcome.step2_desc', tags: [{ key: 'welcome.step2_tag1', color: 'orange' }, { key: 'welcome.step2_tag2', color: 'orange' }] },
  { num: 3, titleKey: 'welcome.step3_title', descKey: 'welcome.step3_desc', tags: [{ key: 'welcome.step3_tag', color: 'purple' }] },
];

function StepCard({ step }: { step: StepDef }) {
  const { t } = useTranslation();
  return (
    <div className="w-[350px] flex flex-col gap-4">
      {/* 번호 박스 */}
      <div className="w-16 h-16 rounded-[16px] bg-[var(--color-neutral-100)] border border-[var(--color-neutral-200)] flex items-center justify-center text-[28px] font-semibold text-[var(--color-neutral-700)]">
        {step.num}
      </div>
      <h3 className="text-[20px] font-semibold text-[var(--color-neutral-800)] tracking-[-0.5px] leading-[1.4]">
        {t(step.titleKey)}
      </h3>
      <p className="text-[15px] font-normal text-[var(--color-neutral-500)] tracking-[-0.375px] leading-[1.6]">
        <Trans i18nKey={step.descKey} components={HIGHLIGHT_COMPONENTS} />
      </p>
      <div className="flex flex-wrap gap-2">
        {step.tags.map((tag) => (
          <Tag key={tag.key} label={t(tag.key)} color={tag.color} />
        ))}
      </div>
    </div>
  );
}

/* ── 메인 컴포넌트 ── */
export default function ProjectWelcomePage() {
  const navigate     = useNavigate();
  const { t }        = useTranslation();
  const queryClient  = useQueryClient();
  const [isModalOpen,    setIsModalOpen]    = useState(false);
  const [isSubmitting,   setIsSubmitting]   = useState(false);
  const [createdProject, setCreatedProject] = useState<Project | null>(null);

  const handleCreateProject = async (data: CreateProjectData) => {
    setIsSubmitting(true);
    try {
      const res = await createProject({
        projectName:        data.name,
        projectDescription: data.goal,
        projectType:        data.projectType,
        projectModuleType:  data.projectModuleType,
      });
      if (data.projectType === 'EXTERNAL' && data.packageKey) {
        await updatePackageKey(res.data.data.projectId, data.packageKey);
      }
      await queryClient.invalidateQueries({ queryKey: ['projects'] });
      setIsModalOpen(false);
      setCreatedProject(res.data.data);
    } catch {
      // 에러 시 모달 유지 (사용자가 재시도 가능)
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="flex flex-col h-screen overflow-hidden bg-[var(--color-bg-page)] min-w-[1280px]">
      <TopNav minimal />

      <main className="flex-1 overflow-y-auto">
        <div className="mx-auto w-full max-w-[1600px] px-8 py-12">

          {/* ── 히어로 ── */}
          <div className="flex flex-col items-center text-center gap-4">
            <span className="inline-flex items-center px-4 py-1.5 rounded-full bg-[var(--color-primary-100)] text-[var(--color-primary-400)] text-[14px] font-semibold tracking-[-0.35px]">
              {t('welcome.badge')}
            </span>
            <h1 className="text-[32px] font-semibold text-[var(--color-neutral-800)] tracking-[-0.8px] leading-[1.4]">
              {t('welcome.title')} <span aria-hidden>🎉</span>
            </h1>
            <p className="max-w-[640px] text-[16px] font-normal text-[var(--color-neutral-500)] tracking-[-0.4px] leading-[1.6]">
              <Trans i18nKey="welcome.subtitle" components={HIGHLIGHT_COMPONENTS} />
            </p>
          </div>

          {/* ── 3스텝 가이드 ── */}
          <div className="flex items-start justify-center gap-6 mt-16">
            {STEPS.map((step, idx) => (
              <Fragment key={step.num}>
                <StepCard step={step} />
                {idx < STEPS.length - 1 && (
                  <div className="flex-shrink-0 pt-6">
                    <ChevronRightIcon />
                  </div>
                )}
              </Fragment>
            ))}
          </div>

          {/* ── CTA ── */}
          <div className="flex justify-center mt-16">
            <button
              type="button"
              onClick={() => setIsModalOpen(true)}
              className="inline-flex items-center justify-center gap-2 px-8 h-14 rounded-[12px] bg-[var(--color-link-blue)] hover:bg-[var(--color-link-blue-hover)] text-white text-[16px] font-semibold tracking-[-0.4px] transition-colors"
            >
              {t('welcome.cta')}
              <ArrowRightIcon />
            </button>
          </div>
        </div>
      </main>

      <CreateProjectModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSubmit={handleCreateProject}
        loading={isSubmitting}
      />

      {createdProject && (
        <ProjectCreatedPanel
          projectName={createdProject.projectName}
          purpose={createdProject.projectDescription}
          apiKey={createdProject.apiKey ?? ''}
          demoUrl={`${MOBILE_BASE}?apiKey=${createdProject.apiKey ?? ''}`}
          onGoDashboard={() => navigate('/dashboard')}
        />
      )}
    </div>
  );
}
