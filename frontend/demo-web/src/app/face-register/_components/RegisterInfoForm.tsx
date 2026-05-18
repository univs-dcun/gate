"use client";
import { translations } from "@/lib/translations";

type Tx = typeof translations["ko"];
type ScanStatus = "idle" | "scanning" | "complete";

interface Props {
  userName: string;
  memo: string;
  scanStatus: ScanStatus;
  isLoading: boolean;
  tx: Tx;
  onUserNameChange: (v: string) => void;
  onMemoChange: (v: string) => void;
  onSubmit: () => void;
}

export function RegisterInfoForm({ userName, memo, scanStatus, isLoading, tx, onUserNameChange, onMemoChange, onSubmit }: Props) {
  const canSubmit = scanStatus === "complete" && !isLoading && userName.trim();
  return (
    <section className="flex flex-col gap-6">
      <div className="bg-white border border-slate-100 p-6 rounded-[2.5rem]"
        style={{ boxShadow: "0 20px 25px -5px rgba(148,163,184,0.2), 0 8px 10px -6px rgba(148,163,184,0.1)" }}
      >
        <div className="flex items-center gap-2.5 mb-8">
          <div className="w-1 h-4 bg-blue-600 rounded-full" />
          <h5 className="font-semibold text-base text-slate-900">{tx.enterRegInfo}</h5>
        </div>
        <div className="flex flex-col gap-7">
          <div>
            <label className="text-[11px] font-semibold text-slate-400 mb-2.5 block ml-1 uppercase tracking-wider">{tx.nameLabel}</label>
            <div className="relative">
              <svg className="absolute left-4 top-1/2 -translate-y-1/2" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#CBD5E1" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
              </svg>
              <input
                type="text"
                placeholder={tx.namePlaceholder}
                value={userName}
                onChange={(e) => onUserNameChange(e.target.value)}
                className="w-full bg-slate-50 border-2 border-transparent focus:border-blue-500 focus:bg-white rounded-2xl pl-11 pr-4 py-3.5 text-sm font-medium transition-all outline-none"
              />
            </div>
          </div>
          <div>
            <label className="text-[11px] font-semibold text-slate-400 mb-2.5 block ml-1 uppercase tracking-wider">{tx.memoLabel}</label>
            <div className="relative">
              <svg className="absolute left-4 top-1/2 -translate-y-1/2" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#CBD5E1" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z" />
                <path d="M14 2v4a2 2 0 0 0 2 2h4" />
                <path d="M10 9H8" /><path d="M16 13H8" /><path d="M16 17H8" />
              </svg>
              <input
                type="text"
                placeholder={tx.memoPlaceholder}
                value={memo}
                onChange={(e) => onMemoChange(e.target.value)}
                className="w-full bg-slate-50 border-2 border-transparent focus:border-blue-500 focus:bg-white rounded-2xl pl-11 pr-4 py-3.5 text-sm font-medium transition-all outline-none"
              />
            </div>
            <p className="text-[10px] text-slate-400 mt-2.5 ml-1 leading-relaxed font-medium">{tx.memoHint}</p>
          </div>
        </div>
      </div>

      <button
        disabled={!canSubmit}
        onClick={onSubmit}
        className={`w-full font-semibold rounded-[14px] py-4 transition-all flex items-center justify-center gap-2 active:scale-[0.97] ${
          canSubmit ? "bg-[#006FFF] text-white" : "bg-slate-200 text-slate-400 cursor-not-allowed"
        }`}
      >
        {isLoading && <div className="w-5 h-5 rounded-full border-2 border-white/30 border-t-white animate-spin" />}
        <span className="text-[16px] font-semibold tracking-[-0.4px] leading-[1.4]">
          {isLoading ? tx.registering : tx.completeReg}
        </span>
      </button>
    </section>
  );
}
