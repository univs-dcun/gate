import { useState, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { DashboardLayout } from '@/components/layout';
import { LogTable, DateRangePicker } from '@/components/common';
import { SearchBlueIcon, RefreshIcon, FilterModuleIcon, FilterResultIcon, ModuleTypeIcon } from '@/components/ui/icons';
import type { LogModule, LogResult, DateRangeValue } from '@/components/common';
import { getLogs, toLogEntry } from '@/services/log';
import type { MatchingResultType } from '@/services/log';
import { useProjectContext } from '@/contexts/ProjectContext';

/* ─── 모듈 색상 매핑 ── */
const MODULE_COLOR: Record<LogModule, string> = {
  '등록':        '#4bbbfb',
  '1:1 촬영인증': '#f59e0b',
  '1:1 사진인증': '#02aaa4',
  '1:N 매칭':    '#8b5cf6',
  '라이브니스':   '#10b981',
};

/* ── 필터 → API 파라미터 변환 ── */
const MODULE_TO_API: Record<LogModule, string> = {
  '등록':        'REGISTER',
  '1:1 촬영인증': 'VERIFY_ID',
  '1:1 사진인증': 'VERIFY_IMAGE',
  '1:N 매칭':    'IDENTIFY',
  '라이브니스':   'LIVENESS',
};

const RESULT_TO_API: Record<LogResult, MatchingResultType> = {
  '성공':  'SUCCESS',
  '리얼':  'SUCCESS',
  '실패':  'FAILURE',
  '페이크': 'FAILURE',
};

/* ─── 필터 아이콘 SVG ────────────────────────────────────── */
const ChevronDown = ({ open }: { open: boolean }) => (
  <svg
    width="20" height="20" viewBox="0 0 24 24" fill="none"
    stroke="#64748b" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"
    style={{ transform: open ? 'rotate(180deg)' : 'none', transition: 'transform 0.15s' }}
  >
    <polyline points="6 9 12 15 18 9" />
  </svg>
);

/* ── 드롭다운 공통 트리거 스타일 ── */
const TRIGGER_BASE = [
  'flex items-center gap-1 bg-white border border-[#cbd5e1] rounded-[8px]',
  'pl-[10px] pr-3 py-3 cursor-pointer select-none',
].join(' ');

const DROPDOWN_BASE = [
  'absolute top-[calc(100%+4px)] left-0 z-50 min-w-[160px]',
  'bg-white rounded-[8px] shadow-[0px_9.8px_40px_9.8px_rgba(140,152,164,0.18)]',
  'p-2 overflow-hidden',
].join(' ');

const DROPDOWN_ITEM = [
  'w-full flex items-center gap-1.5 px-2 py-2 rounded-[8px]',
  'text-[14px] font-medium text-[#475569] tracking-[-0.35px] cursor-pointer text-left',
  'hover:bg-[#f1f5f9] transition-colors',
].join(' ');

/* ─── 모듈 드롭다운 아이콘 ─────────────────────────────── */
function ModuleItemIcon({ module }: { module: string }) {
  return <ModuleTypeIcon module={module} size={20} />;
}

/* ── 필터 아이템 상수 ── */
const MODULE_ITEMS: LogModule[] = ['등록', '1:1 촬영인증', '1:1 사진인증', '1:N 매칭', '라이브니스'];
const RESULT_ITEMS: LogResult[] = ['성공', '실패'];

/* ── i18n 키 매핑 ── */
const MODULE_I18N: Record<LogModule, string> = {
  '등록':        'module.enrollment',
  '1:1 촬영인증': 'module.verification',
  '1:1 사진인증': 'module.verify_image',
  '1:N 매칭':    'module.matching',
  '라이브니스':   'module.liveness',
};

const RESULT_I18N: Record<LogResult, string> = {
  '성공':  'logs.success',
  '리얼':  'logs.real',
  '실패':  'logs.failure',
  '페이크': 'logs.fake',
};


/* ─── 메인 페이지 ─────────────────────────────────────────── */
const LOG_DATE_RANGE_KEY = 'log_date_range';

function loadSavedDateRange(todayStr: string): DateRangeValue {
  try {
    const saved = localStorage.getItem(LOG_DATE_RANGE_KEY);
    if (saved) return JSON.parse(saved) as DateRangeValue;
  } catch { /* ignore */ }
  return { startDate: todayStr, endDate: todayStr, preset: 'today' };
}

function LogDetailPage() {
  const { t } = useTranslation();
  const { selectedId } = useProjectContext();
  const location = useLocation();
  const [searchParams] = useSearchParams();

  const todayStr = new Intl.DateTimeFormat('sv-SE', {
    timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
  }).format(new Date());

  const [moduleFilter, setModuleFilter] = useState<LogModule | ''>('');
  const [resultFilter, setResultFilter] = useState<LogResult | ''>('');
  /* 인증방식 탭 (기본: 모든 방식). 얼굴/손바닥 필터는 백엔드 modality 지원 시 API 파라미터로 연결 */
  const [authTab, setAuthTab] = useState<'all' | 'face' | 'palm'>('all');
  const initialFid = searchParams.get('fid') ?? '';
  const [searchInput, setSearchInput] = useState(initialFid);
  const [searchQuery, setSearchQuery] = useState(initialFid);
  const [dateRangeValue, setDateRangeValue] = useState<DateRangeValue>(() => loadSavedDateRange(todayStr));
  const [page, setPage]         = useState(1);
  const [pageSize, setPageSize] = useState(10);

  /* 프로젝트 변경 시 필터/페이지 초기화 — 마운트 시엔 실행하지 않음 */
  const prevSelectedIdRef = useRef(selectedId);
  useEffect(() => {
    const prev = prevSelectedIdRef.current;
    prevSelectedIdRef.current = selectedId;
    if (prev && selectedId && prev !== selectedId) {
      setModuleFilter('');
      setResultFilter('');
      setSearchInput('');
      setSearchQuery('');
      setPage(1);
    }
  }, [selectedId]);

  const [moduleDropdownOpen, setModuleDropdownOpen] = useState(false);
  const [resultDropdownOpen, setResultDropdownOpen] = useState(false);

  const moduleRef = useRef<HTMLDivElement>(null);
  const resultRef = useRef<HTMLDivElement>(null);

  /* 외부 클릭 시 드롭다운 닫기 */
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (moduleRef.current && !moduleRef.current.contains(e.target as Node)) {
        setModuleDropdownOpen(false);
      }
      if (resultRef.current && !resultRef.current.contains(e.target as Node)) {
        setResultDropdownOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  /* 필터 변경 시 페이지 초기화 */
  const handleModuleFilter    = (m: LogModule | '')  => { setModuleFilter(m); setPage(1); };
  const handleResultFilter    = (r: LogResult | '')  => { setResultFilter(r); setPage(1); };
  const handleDateRangeChange = (v: DateRangeValue)  => {
    setDateRangeValue(v);
    setPage(1);
    localStorage.setItem(LOG_DATE_RANGE_KEY, JSON.stringify(v));
  };
  const applySearch = () => { setSearchQuery(searchInput.trim()); setPage(1); };
  const handleReset = () => {
    const defaultRange = { startDate: todayStr, endDate: todayStr, preset: 'today' } as DateRangeValue;
    setModuleFilter('');
    setResultFilter('');
    setSearchInput('');
    setSearchQuery('');
    setDateRangeValue(defaultRange);
    setPage(1);
    localStorage.setItem(LOG_DATE_RANGE_KEY, JSON.stringify(defaultRange));
  };

  /* API 쿼리 파라미터 구성 */
  const { startDate, endDate } = dateRangeValue;
  const queryParams = {
    matchType:       moduleFilter ? MODULE_TO_API[moduleFilter] : undefined,
    matchResultType: resultFilter ? RESULT_TO_API[resultFilter] : 'ALL' as MatchingResultType,
    matchingKeyword: searchQuery || undefined,
    featureType:     authTab !== 'all' ? (authTab === 'palm' ? 'PALM' as const : 'FACE' as const) : undefined,
    startDate,
    endDate,
    page,
    pageSize,
  };

  const { data: apiData, isLoading, refetch } = useQuery({
    queryKey: ['logs', selectedId, queryParams],
    queryFn:  () => getLogs(queryParams).then((r) => r.data.data),
    staleTime: 0,
  });

  /* 사이드바 메뉴 클릭 등 페이지 재진입 시 강제 갱신 */
  useEffect(() => {
    refetch();
  }, [location.key]); // eslint-disable-line react-hooks/exhaustive-deps

  const logs         = (apiData?.contents ?? []).map(toLogEntry);
  const totalCount   = apiData?.page.totalElements ?? 0;
  const totalRecords = apiData?.page.totalCount;

  return (
    <DashboardLayout>
      <div className="flex flex-col gap-5">

        {/* 페이지 제목 */}
        <h1 className="text-[26px] font-semibold text-[#1e293b] tracking-[-0.65px] leading-[1.4]">
          {t('logs.title')}
        </h1>

        {/* 인증방식 탭 (모든 방식 / 얼굴 / 손바닥) */}
        <div className="flex items-center gap-4 border-b border-[#cbd5e1]">
          {([
            { id: 'all',  label: t('logs.tab_all') },
            { id: 'face', label: t('auth_type.face') },
            { id: 'palm', label: t('auth_type.palm') },
          ] as const).map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => { setAuthTab(tab.id); setPage(1); }}
              className={[
                'px-3 py-2 text-[16px] leading-[24px] tracking-[-0.4px] border-b-2 -mb-px transition-colors',
                authTab === tab.id
                  ? 'border-[var(--color-link-blue)] text-[var(--color-link-blue)] font-semibold'
                  : 'border-transparent text-[#94a3b8] font-normal hover:text-[#475569]',
              ].join(' ')}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* 필터 바 */}
        <div className="flex items-center justify-between gap-4">
          {/* 좌측: [모듈] [검색바] [결과] [검색버튼] [초기화버튼] */}
          <div className="flex items-center gap-3">

            {/* 검색 바 */}
            <div className="flex items-center bg-white border border-[#cbd5e1] rounded-[8px] px-3 py-3 w-[300px]">
              <input
                type="text"
                placeholder={t('logs.search_placeholder')}
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') applySearch(); }}
                className="flex-1 text-[14px] font-medium text-[#1e293b] placeholder-[#64748b] tracking-[-0.35px] bg-transparent outline-none"
              />
            </div>

            {/* 테스트 모듈 드롭다운 */}
            <div className="relative" ref={moduleRef}>
              <button
                className={[TRIGGER_BASE, 'min-w-[150px] whitespace-nowrap'].join(' ')}
                onClick={() => { setModuleDropdownOpen((o) => !o); setResultDropdownOpen(false); }}
              >
                {moduleFilter ? <ModuleItemIcon module={moduleFilter} /> : <FilterModuleIcon size={20} />}
                <span className="flex items-center gap-1.5 text-left whitespace-nowrap">
                  <span className="text-[14px] font-medium text-[#64748b] tracking-[-0.35px]">{t('logs.test_module')}</span>
                  <span className="text-[14px] font-semibold tracking-[-0.35px]" style={{ color: moduleFilter ? MODULE_COLOR[moduleFilter] : '#334155' }}>
                    {moduleFilter ? t(MODULE_I18N[moduleFilter]) : t('common.all')}
                  </span>
                </span>
                <ChevronDown open={moduleDropdownOpen} />
              </button>
              {moduleDropdownOpen && (
                <div className={DROPDOWN_BASE}>
                  <div className="px-2 py-1 mb-1">
                    <span className="text-[13px] text-[#64748b] tracking-[-0.325px]">{t('logs.test_module')}</span>
                  </div>
                  {MODULE_ITEMS.map((m) => (
                    <button key={m} className={DROPDOWN_ITEM}
                      onClick={() => { handleModuleFilter(moduleFilter === m ? '' : m); setModuleDropdownOpen(false); }}
                    >
                      <ModuleItemIcon module={m} />
                      <span style={{ color: MODULE_COLOR[m] }}>{t(MODULE_I18N[m])}</span>
                    </button>
                  ))}
                  <div className="border-t border-[#e2e8f0] mt-1 pt-1">
                    <button className={[DROPDOWN_ITEM, 'text-[#757b80] font-normal text-[13px]'].join(' ')}
                      onClick={() => { handleModuleFilter(''); setModuleDropdownOpen(false); }}>
                      {t('common.all')}
                    </button>
                  </div>
                </div>
              )}
            </div>

            {/* 결과 드롭다운 */}
            <div className="relative" ref={resultRef}>
              <button
                className={[TRIGGER_BASE, 'min-w-[140px] whitespace-nowrap'].join(' ')}
                onClick={() => { setResultDropdownOpen((o) => !o); setModuleDropdownOpen(false); }}
              >
                <FilterResultIcon size={20} />
                <span className="flex items-center gap-1.5 text-left whitespace-nowrap">
                  <span className="text-[14px] font-medium text-[#64748b] tracking-[-0.35px]">{t('logs.result')}</span>
                  <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px]">
                    {resultFilter ? t(RESULT_I18N[resultFilter]) : t('common.all')}
                  </span>
                </span>
                <ChevronDown open={resultDropdownOpen} />
              </button>
              {resultDropdownOpen && (
                <div className={DROPDOWN_BASE}>
                  <div className="px-2 py-1 mb-1">
                    <span className="text-[13px] text-[#64748b] tracking-[-0.325px]">{t('logs.result')}</span>
                  </div>
                  {RESULT_ITEMS.map((r) => (
                    <button key={r} className={DROPDOWN_ITEM}
                      onClick={() => { handleResultFilter(resultFilter === r ? '' : r); setResultDropdownOpen(false); }}>
                      {t(RESULT_I18N[r])}
                    </button>
                  ))}
                  <div className="border-t border-[#e2e8f0] mt-1 pt-1">
                    <button className={[DROPDOWN_ITEM, 'text-[#757b80] font-normal text-[13px]'].join(' ')}
                      onClick={() => { handleResultFilter(''); setResultDropdownOpen(false); }}>
                      {t('common.all')}
                    </button>
                  </div>
                </div>
              )}
            </div>

            {/* 검색 버튼 — 아웃라인 + 검색 아이콘 */}
            <button onClick={applySearch}
              className="h-[44px] inline-flex items-center gap-1 px-3 rounded-[8px] bg-white border border-[#006fff] text-[#006fff] text-[14px] font-semibold tracking-[-0.35px] hover:bg-[#eff9ff] transition-colors whitespace-nowrap">
              <SearchBlueIcon size={18} />
              {t('common.search')}
            </button>

            {/* 초기화 버튼 — refresh 아이콘 */}
            <button onClick={handleReset} title={t('common.reset')}
              className="h-[44px] w-[44px] flex items-center justify-center rounded-[8px] border border-[#cbd5e1] bg-white hover:bg-[#f1f5f9] transition-colors">
              <RefreshIcon size={20} />
            </button>
          </div>

          {/* 우측: 날짜 범위 피커 */}
          <div className="flex items-center gap-2">
            <DateRangePicker
              value={dateRangeValue}
              onChange={handleDateRangeChange}
            />
          </div>
        </div>

        {/* 로그 테이블 */}
        <LogTable
          data={logs}
          totalCount={totalCount}
          totalRecords={totalRecords}
          page={page}
          pageSize={pageSize}
          onPageChange={setPage}
          onPageSizeChange={(size) => { setPageSize(size); setPage(1); }}
          isLoading={isLoading}
        />

      </div>
    </DashboardLayout>
  );
}

export default LogDetailPage;
