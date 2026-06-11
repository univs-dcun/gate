/**
 * ProjectCombobox — 프로젝트 선택 콤보박스
 *
 * Figma node 285-2677 (트리거 버튼) + 84-1712 (드롭다운 패널) 기반
 *
 * 닫힌 상태: 현재 프로젝트 + 인증 타입 + 아래 화살표
 * 열린 상태: 드롭다운 패널 (현재 선택 헤더 + 목록 + 전체보기)
 */

import { useState, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';

export interface ProjectOption {
  id: string;
  name: string;
  authType: string;
  apiKey: string;
  colorTag?: string;   // 프로젝트 구분용 색상
  projectType?: 'STANDARD' | 'EXTERNAL';
  projectModuleType?: 'FACE' | 'PALM';
  planType?: 'FREE' | 'STARTER' | 'BUSINESS' | 'SECURITY_PLUS' | 'ENTERPRISE';
  planStart?: string;   // createdAt (YYYY-MM-DD or ISO string)
  planExpiry?: string;  // planExpiredAt (YYYY-MM-DD or ISO string)
  description?: string; // projectDescription
}

interface Props {
  projects: ProjectOption[];
  selectedId: string;
  onSelect: (id: string) => void;
  onViewAll?: () => void;
}

/* ── 프로젝트 색상 스와치 (colorTag로 구분) ── */
function ColorSwatch({ color, className }: { color?: string; className?: string }) {
  return (
    <span
      className={['flex-shrink-0 w-9 h-9 rounded-[8px] border border-[var(--color-neutral-300)]', className ?? ''].join(' ')}
      style={{ backgroundColor: color || '#e2e8f0' }}
      aria-hidden
    />
  );
}


/* ── 화살표 아이콘 (열릴 때 180° 회전) ── */
function ArrowIcon({ open }: { open: boolean }) {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 20 20"
      fill="none"
      className={['transition-transform duration-200 flex-shrink-0', open ? 'rotate-180' : ''].join(' ')}
      aria-hidden="true"
    >
      <path
        d="M5 7.5L10 12.5L15 7.5"
        stroke="#94A3B8"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

/* ── 스타일 상수 ── */
const TRIGGER = [
  'flex flex-col items-start w-full text-left',
  'bg-white border-0 rounded-[8px]',
  'cursor-pointer transition-shadow',
  'hover:shadow-sm',
].join(' ');

const DROPDOWN_PANEL = [
  'absolute left-0 top-full w-full mt-1',
  'bg-white border border-[var(--color-border-dropdown)]',
  'rounded-[var(--radius-xs)] p-2 overflow-hidden',
  'shadow-[var(--shadow-dropdown)]',
  'z-[var(--z-dropdown)]',
].join(' ');

const ITEM_BASE = [
  'flex items-center gap-2 w-full text-left',
  'px-1.5 py-2 min-h-[55px] rounded-[var(--radius-xl)] cursor-pointer',
  'transition-colors',
].join(' ');


export default function ProjectCombobox({ projects, selectedId, onSelect, onViewAll }: Props) {
  const { t } = useTranslation();
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const selected = projects.find((p) => p.id === selectedId) ?? projects[0];

  /* 외부 클릭 시 닫기 */
  useEffect(() => {
    if (!isOpen) return;
    function handleOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    }
    document.addEventListener('mousedown', handleOutside);
    return () => document.removeEventListener('mousedown', handleOutside);
  }, [isOpen]);

  return (
    <div ref={containerRef} className="relative w-full">

      {/* ── 트리거 버튼 (닫힌 상태) ── */}
      <button
        type="button"
        onClick={() => setIsOpen((v) => !v)}
        className={TRIGGER}
        style={{ padding: '16px 12px 12px 12px' }}
      >
        {/* 상단: 아이콘 + 프로젝트명 + 프로젝트 타입 */}
        <div className="flex items-start gap-2 w-full">
          <ColorSwatch color={selected?.colorTag} />
          <div className="flex flex-col gap-0.5 min-w-0 flex-1">
            <span className="text-[14px] font-semibold text-[var(--color-neutral-800)] tracking-[-0.35px] leading-[1.4] truncate">
              {selected?.name ?? ''}
            </span>
          </div>
        </div>
        {/* 구분선 */}
        {/* <div className="mt-3 w-full border-t border-[var(--color-border-default)]" /> */}
        {/* 하단: 프로젝트 리스트 + chevron */}
        <div className="flex items-center justify-between w-full pt-3">
          <span className="text-[13px] font-medium text-[var(--color-text-tertiary)] tracking-[-0.3px] leading-[1.4]">
            {t('projects.list')}
          </span>
          <ArrowIcon open={isOpen} />
        </div>
      </button>

      {/* ── 드롭다운 패널 (열린 상태) ── */}
      {isOpen && (
        <div className={DROPDOWN_PANEL}>

          {/* 프로젝트 목록 — 현재 선택된 항목 제외 */}
          <div className="full flex-col gap-2">
            {projects.filter((p) => p.id !== selectedId).map((p) => (
                <button
                  key={p.id}
                  type="button"
                  onClick={() => { onSelect(p.id); setIsOpen(false); }}
                  className={[ITEM_BASE, 'hover:bg-[var(--color-surface-layer1)]'].join(' ')}
                >
                  {/* 프로젝트 색상 스와치 */}
                  <ColorSwatch color={p.colorTag} />
                  <span className="flex flex-col gap-0.5 items-start text-left min-w-0">
                    {/* 이름: Regular 14px, text/primary #17191A, lh 20px */}
                    <span className="text-[14px] font-normal text-[var(--color-text-primary)] tracking-[-0.35px] leading-[20px] truncate">
                      {p.name}
                    </span>
                  </span>
                </button>
            ))}
          </div>

          {/* 구분선 */}
          <div className="my-2 border-t border-[var(--color-border-dropdown)]" />

          {/* 전체보기 — Label3: Regular 13px, text/tertiary #757B80 */}
          <button
            type="button"
            onClick={() => { onViewAll?.(); setIsOpen(false); }}
            className="flex w-full items-center p-2 rounded-[var(--radius-xl)] hover:bg-[var(--color-surface-layer1)] transition-colors"
          >
            <span className="text-[13px] font-normal text-[var(--color-text-tertiary)] tracking-[-0.025px] leading-[16px]">
              {t('projects.view_all')}
            </span>
          </button>
        </div>
      )}
    </div>
  );
}
