export interface GuideStep {
  title: string;        // 단계 제목 (16px SemiBold)
  description: string;  // 단계 설명 (14px neutral-400)
}

interface StepGuideProps {
  guideTitle: string;
  steps: GuideStep[];
}

/**
 * 모듈 테스트 페이지 우측 패널 — 단계별 안내 가이드 (Figma 오른쪽 패널 기준)
 *
 * [번호 원 40px] ─── [title 16px SemiBold]
 *       │              [description 14px neutral-400]
 *       │ (44px 연결선)
 * [번호 원 40px] ─── …
 */
function StepGuide({ guideTitle, steps }: StepGuideProps) {
  return (
    <div className="flex flex-col gap-4 p-5 bg-[var(--card-bg)] border border-[var(--card-border)] rounded-[var(--card-radius)] shadow-[var(--card-shadow)]">
      <h3 className="text-[length:var(--text-base)] font-semibold text-[#1e293b] tracking-[-0.4px] leading-[var(--leading-normal)]">
        {guideTitle}
      </h3>

      <div className="flex flex-col">
        {steps.map((step, idx) => {
          const isLast = idx === steps.length - 1;
          return (
            <div key={idx} className="flex gap-3">
              {/* 좌측: 번호 원 + 수직 연결선 */}
              <div className="flex flex-col items-center">
                {/* 번호 원 40px */}
                <div className="flex-shrink-0 w-10 h-10 rounded-full border border-[#94a3b8] bg-white flex items-center justify-center">
                  <span className="text-[length:var(--text-sm)] font-medium text-[#94a3b8]">
                    {idx + 1}
                  </span>
                </div>
                {/* 수직 연결선 */}
                {!isLast && (
                  <div className="w-[2px] h-[44px] my-1 bg-[var(--color-neutral-200)]" />
                )}
              </div>

              {/* 우측: 텍스트 */}
              <div className={['flex-1 min-w-0 pt-2', isLast ? '' : 'pb-2'].join(' ')}>
                <p className="text-[length:var(--text-base)] font-semibold text-[#1e293b] tracking-[-0.4px] leading-[var(--leading-normal)]">
                  {step.title}
                </p>
                <p className="mt-0.5 text-[length:var(--text-sm)] font-normal text-[var(--color-neutral-400)] tracking-[-0.35px] leading-[var(--leading-l)]">
                  {step.description}
                </p>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default StepGuide;
