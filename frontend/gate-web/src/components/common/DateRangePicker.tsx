import { useState, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';

/* ── 타입 ── */
export interface DateRangeValue {
  startDate: string; // YYYY-MM-DD
  endDate: string;   // YYYY-MM-DD
  preset?: string;   // 빠른 선택 레이블 (없으면 커스텀)
}

interface Props {
  value: DateRangeValue;
  onChange: (v: DateRangeValue) => void;
}

/* ── 브라우저 타임존 (Accept-TimeZone 헤더와 동일) ── */
const LOCAL_TZ = Intl.DateTimeFormat().resolvedOptions().timeZone;

/* ── 유틸 ── */
function toStr(d: Date): string {
  // 서버에 전달하는 날짜는 Accept-TimeZone 헤더와 동일한 로컬 타임존 기준
  return new Intl.DateTimeFormat('sv-SE', { timeZone: LOCAL_TZ }).format(d);
}
function fromStr(s: string): Date {
  const [y, m, day] = s.split('-').map(Number);
  return new Date(y, m - 1, day);
}
function formatLabel(s: string): string {
  // YYYY-MM-DD → YY/MM/DD
  const [y, m, d] = s.split('-');
  return `${y.slice(2)}/${m}/${d}`;
}
function isSameDay(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate();
}
function isInRange(day: Date, start: Date | null, end: Date | null) {
  if (!start || !end) return false;
  const t = day.getTime();
  return t > start.getTime() && t < end.getTime();
}
function getDaysGrid(year: number, month: number): (number | null)[] {
  // month: 0-based
  const first = new Date(year, month, 1).getDay(); // 0=Sun
  const last  = new Date(year, month + 1, 0).getDate();
  const cells: (number | null)[] = Array(first).fill(null);
  for (let i = 1; i <= last; i++) cells.push(i);
  // 마지막 주를 7칸으로 채워 flex-1 셀 폭 정렬 유지
  while (cells.length % 7 !== 0) cells.push(null);
  return cells;
}
function getToday() {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

/* ── 빠른 선택 프리셋 ── */
function getPresetRange(preset: string): { startDate: string; endDate: string } {
  const today = getToday();
  const end   = toStr(today);
  if (preset === 'today') return { startDate: end, endDate: end };
  if (preset === '7days') {
    const s = new Date(today); s.setDate(today.getDate() - 7);
    return { startDate: toStr(s), endDate: end };
  }
  if (preset === 'this_month') {
    const s = new Date(today.getFullYear(), today.getMonth(), 1);
    return { startDate: toStr(s), endDate: end };
  }
  if (preset === '3months') {
    const s = new Date(today); s.setMonth(today.getMonth() - 3);
    return { startDate: toStr(s), endDate: end };
  }
  if (preset === '6months') {
    const s = new Date(today); s.setMonth(today.getMonth() - 6);
    return { startDate: toStr(s), endDate: end };
  }
  if (preset === 'this_year') {
    const s = new Date(today.getFullYear(), 0, 1);
    return { startDate: toStr(s), endDate: end };
  }
  // all
  return { startDate: '', endDate: '' };
}

const PRESETS = ['today', '7days', 'this_month', '3months', '6months', 'this_year'];
const PRESET_I18N: Record<string, string> = {
  today:      'logs.date_today',
  '7days':    'logs.date_7days',
  this_month: 'logs.date_this_month',
  '3months':  'logs.date_3months',
  '6months':  'logs.date_6months',
  this_year:  'logs.date_this_year',
  all:        'logs.date_all',
};

/* ── 아이콘 ── */
const CalendarIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
    <line x1="16" y1="2" x2="16" y2="6" />
    <line x1="8" y1="2" x2="8" y2="6" />
    <line x1="3" y1="10" x2="21" y2="10" />
  </svg>
);
const ChevronUpIcon = ({ open }: { open: boolean }) => (
  <svg
    width="20" height="20" viewBox="0 0 24 24" fill="none"
    stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"
    style={{ transform: open ? 'rotate(180deg)' : 'none', transition: 'transform 0.15s' }}
  >
    <polyline points="18 15 12 9 6 15" />
  </svg>
);
const ChevronLeftIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#1e293b" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="15 18 9 12 15 6" />
  </svg>
);
const ChevronRightIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#1e293b" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="9 18 15 12 9 6" />
  </svg>
);

/* ── DateCell ── */
interface CellProps {
  day: number | null;
  year: number;
  month: number;
  startDate: Date | null;
  endDate: Date | null;
  hoverDate: Date | null;
  onClick: (d: Date) => void;
  onHover: (d: Date | null) => void;
}
function DateCell({ day, year, month, startDate, endDate, hoverDate, onClick, onHover }: CellProps) {
  if (!day) return <div className="flex-1 h-[36px]" />;

  const date    = new Date(year, month, day);
  const today   = getToday();
  const isToday = isSameDay(date, today);
  const isFuture = date > today;

  const isStart = startDate ? isSameDay(date, startDate) : false;
  const isEnd   = endDate   ? isSameDay(date, endDate)   : false;
  const isSelected = isStart || isEnd;

  // 종료날짜 미확정 시 hover로 범위 미리보기
  const effectiveEnd = endDate ?? hoverDate;
  const rangeStart = startDate && effectiveEnd
    ? (startDate < effectiveEnd ? startDate : effectiveEnd)
    : null;
  const rangeEnd = startDate && effectiveEnd
    ? (startDate < effectiveEnd ? effectiveEnd : startDate)
    : null;

  const inRange   = isInRange(date, rangeStart, rangeEnd);
  const isRangeStart = rangeStart ? isSameDay(date, rangeStart) : false;
  const isRangeEnd   = rangeEnd   ? isSameDay(date, rangeEnd)   : false;

  return (
    <div
      className={['relative flex-1 h-[36px] flex items-center justify-center', isFuture ? 'cursor-not-allowed' : 'cursor-pointer'].join(' ')}
      onClick={() => !isFuture && onClick(date)}
      onMouseEnter={() => !isFuture && onHover(date)}
      onMouseLeave={() => onHover(null)}
    >
      {/* 범위 내 배경 (full-width) */}
      {inRange && (
        <div className="absolute inset-0 bg-[#006fff] opacity-10" />
      )}
      {/* 시작일 — 우측 절반만 range 색 */}
      {isRangeStart && !isRangeEnd && (
        <div className="absolute top-0 right-0 bottom-0 w-1/2 bg-[#006fff] opacity-10" />
      )}
      {/* 종료일 — 좌측 절반만 range 색 */}
      {isRangeEnd && !isRangeStart && (
        <div className="absolute top-0 left-0 bottom-0 w-1/2 bg-[#006fff] opacity-10" />
      )}
      {/* 선택된 날짜 원 */}
      {isSelected && (
        <div className="absolute inset-0 flex items-center justify-center">
          <div className="w-[36px] h-[36px] rounded-full bg-[#006fff]" />
        </div>
      )}
      {/* 날짜 텍스트 */}
      <span className={[
        'relative z-10 text-[14px] tracking-[-0.35px] leading-[20px] w-[36px] text-center',
        isSelected   ? 'text-white font-medium'    :
        isFuture     ? 'text-[#cbd5e1]'            :
        inRange      ? 'text-[#1e293b]'            :
        'text-[#1e293b]',
      ].join(' ')}>
        {day}
      </span>
      {/* 오늘 dot */}
      {isToday && !isSelected && (
        <span className="absolute bottom-[4px] left-1/2 -translate-x-1/2 w-[4px] h-[4px] rounded-full bg-[#006fff]" />
      )}
    </div>
  );
}


/* ── DateRangePicker ── */
export default function DateRangePicker({ value, onChange }: Props) {
  const { t, i18n } = useTranslation();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // 드래프트 상태 (패널 내에서 선택 중)
  const [draftStart, setDraftStart] = useState<Date | null>(null);
  const [draftEnd,   setDraftEnd]   = useState<Date | null>(null);
  const [selecting,  setSelecting]  = useState(false); // true = start 선택됨, end 미선택
  const [hoverDate,  setHoverDate]  = useState<Date | null>(null);
  const [activePreset, setActivePreset] = useState<string | undefined>(value.preset);

  // 뷰 달: 좌측 캘린더 기준
  const initViewMonth = () => {
    const base = value.startDate ? fromStr(value.startDate) : getToday();
    return { year: base.getFullYear(), month: base.getMonth() };
  };
  const [viewYear,  setViewYear]  = useState(initViewMonth().year);
  const [viewMonth, setViewMonth] = useState(initViewMonth().month);

  // 우측 달 계산
  const rightYear  = viewMonth === 11 ? viewYear + 1 : viewYear;
  const rightMonth = viewMonth === 11 ? 0 : viewMonth + 1;
  const locale = i18n.language === 'en' ? 'en-US' : 'ko-KR';
  // 2023-01-01은 일요일 기준 — 0(일)~6(토) 순서로 요일명 생성
  const WEEKDAYS = Array.from({ length: 7 }, (_, i) =>
    new Intl.DateTimeFormat(locale, { weekday: 'short' }).format(new Date(2023, 0, 1 + i)),
  );
  const formatMonthHeader = (year: number, month: number) =>
    new Intl.DateTimeFormat(locale, { year: 'numeric', month: 'long' }).format(new Date(year, month, 1));

  /* 패널 열 때 현재 값으로 드래프트 초기화 */
  const openPanel = () => {
    const start = value.startDate ? fromStr(value.startDate) : null;
    const end   = value.endDate   ? fromStr(value.endDate)   : null;
    setDraftStart(start);
    setDraftEnd(end);
    setSelecting(false);
    setActivePreset(value.preset);
    // 뷰 달을 시작일 기준으로
    const base = start ?? getToday();
    setViewYear(base.getFullYear());
    setViewMonth(base.getMonth());
    setOpen(true);
  };

  /* 외부 클릭 닫기 */
  useEffect(() => {
    function handler(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  /* 날짜 클릭 */
  const handleClickDay = (day: Date) => {
    if (!selecting || !draftStart) {
      // 시작일 선택
      setDraftStart(day);
      setDraftEnd(null);
      setSelecting(true);
      setActivePreset(undefined);
    } else {
      // 종료일 선택
      const start = day < draftStart ? day : draftStart;
      const end   = day < draftStart ? draftStart : day;
      setDraftStart(start);
      setDraftEnd(end);
      setSelecting(false);
      setActivePreset(undefined);
    }
  };

  /* 빠른 선택 */
  const handlePreset = (preset: string) => {
    const range = getPresetRange(preset);
    const start = range.startDate ? fromStr(range.startDate) : null;
    const end   = range.endDate   ? fromStr(range.endDate)   : null;
    setDraftStart(start);
    setDraftEnd(end);
    setSelecting(false);
    setActivePreset(preset);
    // 뷰를 시작일로 이동
    if (start) {
      setViewYear(start.getFullYear());
      setViewMonth(start.getMonth());
    }
  };

  /* 이전/다음 월 */
  const goPrev = () => {
    if (viewMonth === 0) { setViewYear((y) => y - 1); setViewMonth(11); }
    else                  setViewMonth((m) => m - 1);
  };
  const goNext = () => {
    if (viewMonth === 11) { setViewYear((y) => y + 1); setViewMonth(0); }
    else                   setViewMonth((m) => m + 1);
  };

  /* 취소 */
  const handleCancel = () => setOpen(false);

  /* 적용 */
  const handleApply = () => {
    if (!draftStart) return;
    const end = draftEnd ?? draftStart;
    onChange({
      startDate: toStr(draftStart),
      endDate:   toStr(end),
      preset:    activePreset,
    });
    setOpen(false);
  };

  /* 트리거 레이블 */
  const triggerLabel = (() => {
    if (value.preset) {
      const label = t(PRESET_I18N[value.preset] ?? value.preset);
      if (!value.startDate) return label; // 전체
      if (value.startDate === value.endDate) return `${label}(${value.startDate})`;
      return `${label}(${value.startDate}~${value.endDate})`;
    }
    if (value.startDate && value.endDate) {
      return `${formatLabel(value.startDate)}~${formatLabel(value.endDate)}`;
    }
    return t('logs.date_today');
  })();

  const canApply = !!draftStart;

  return (
    <div className="relative" ref={containerRef}>
      {/* 트리거 버튼 */}
      <button
        type="button"
        className={[
          'flex items-center gap-1.5 bg-white border border-[#cbd5e1] rounded-[8px]',
          'pl-[10px] pr-3 py-3 cursor-pointer select-none',
        ].join(' ')}
        onClick={() => open ? setOpen(false) : openPanel()}
      >
        <CalendarIcon />
        <span className="text-[14px] font-medium text-[#64748b] tracking-[-0.35px] whitespace-nowrap">
          {triggerLabel}
        </span>
        <ChevronUpIcon open={open} />
      </button>

      {/* 드롭다운 패널 */}
      {open && (
        <div className={[
          'absolute top-[calc(100%+4px)] right-0 z-50',
          'bg-white border border-[#eff9ff] rounded-[8px]',
          'shadow-[0px_8px_24px_0px_rgba(0,0,0,0.1)]',
          'flex gap-4 p-3',
        ].join(' ')}>
          {/* 좌측: 빠른 선택 */}
          <div className="flex flex-col w-[148px] border-r border-[#e2e8f0] pr-3 shrink-0">
            {PRESETS.map((p) => (
              <button
                key={p}
                type="button"
                onClick={() => handlePreset(p)}
                className={[
                  'relative flex items-center px-2 py-2 rounded-[8px] text-[14px] tracking-[-0.35px] text-left transition-colors',
                  activePreset === p
                    ? 'text-[#006fff]'
                    : 'text-[#1e293b] hover:bg-[#f1f5f9]',
                ].join(' ')}
              >
                {activePreset === p && (
                  <div className="absolute inset-0 bg-[#eff9ff] rounded-[8px]" />
                )}
                <span className="relative">{t(PRESET_I18N[p])}</span>
              </button>
            ))}
          </div>

          {/* 우측: 이중 캘린더 */}
          <div className="flex flex-col gap-4">
            {/* 달 표시 헤더 — 좌측 달 이름 + 전체 네비 */}
            <div className="flex gap-6 items-start">
              {/* 좌측 달 헤더에 연도 표시 */}
              <div className="flex items-center h-[36px] px-2 w-[252px]">
                <button type="button" onClick={goPrev} className="w-[24px] h-[24px] flex items-center justify-center hover:bg-[#f1f5f9] rounded-[6px]">
                  <ChevronLeftIcon />
                </button>
                <div className="flex-1 text-center text-[18px] font-bold text-[#1e293b] tracking-[-0.45px] leading-[24px]">
                  {formatMonthHeader(viewYear, viewMonth)}
                </div>
                <div className="w-[24px]" />
              </div>
              {/* 우측 달 헤더 */}
              <div className="flex items-center h-[36px] px-2 w-[252px]">
                <div className="w-[24px]" />
                <div className="flex-1 text-center text-[18px] font-bold text-[#1e293b] tracking-[-0.45px] leading-[24px]">
                  {formatMonthHeader(rightYear, rightMonth)}
                </div>
                <button type="button" onClick={goNext} className="w-[24px] h-[24px] flex items-center justify-center hover:bg-[#f1f5f9] rounded-[6px]">
                  <ChevronRightIcon />
                </button>
              </div>
            </div>

            {/* 두 캘린더 */}
            <div className="flex gap-6">
              {[
                { year: viewYear,  month: viewMonth },
                { year: rightYear, month: rightMonth },
              ].map(({ year, month }, idx) => {
                const cells = getDaysGrid(year, month);
                return (
                  <div key={idx} className="flex flex-col gap-1 w-[252px]">
                    {/* 요일 헤더 */}
                    <div className="flex">
                      {WEEKDAYS.map((w) => (
                        <div key={w} className="flex-1 h-[36px] flex items-center justify-center text-[14px] text-[#1e293b] tracking-[-0.35px]">
                          {w}
                        </div>
                      ))}
                    </div>
                    {/* 날짜 행 */}
                    {Array.from({ length: Math.ceil(cells.length / 7) }, (_, wi) => (
                      <div key={wi} className="flex">
                        {cells.slice(wi * 7, wi * 7 + 7).map((day, ci) => (
                          <DateCell
                            key={ci}
                            day={day}
                            year={year}
                            month={month}
                            startDate={draftStart}
                            endDate={draftEnd}
                            hoverDate={selecting ? hoverDate : null}
                            onClick={handleClickDay}
                            onHover={setHoverDate}
                          />
                        ))}
                      </div>
                    ))}
                  </div>
                );
              })}
            </div>

            {/* 액션 버튼 */}
            <div className="flex items-center justify-end gap-3">
              <button
                type="button"
                onClick={handleCancel}
                className={[
                  'h-[40px] px-4 border border-[#cbd5e1] rounded-[8px]',
                  'text-[15px] font-medium text-[#1e293b] tracking-[-0.375px]',
                  'hover:bg-[#f8fafc] transition-colors',
                ].join(' ')}
              >
                {t('common.cancel')}
              </button>
              <button
                type="button"
                onClick={handleApply}
                disabled={!canApply}
                className={[
                  'h-[40px] px-4 rounded-[8px]',
                  'text-[15px] font-medium text-white tracking-[-0.375px]',
                  'transition-colors',
                  canApply ? 'bg-[#006fff] hover:bg-[#0060e0]' : 'bg-[#cbd5e1] cursor-not-allowed',
                ].join(' ')}
              >
                {t('logs.date_apply')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
