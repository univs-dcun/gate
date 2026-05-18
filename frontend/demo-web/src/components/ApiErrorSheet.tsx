"use client";
import { ApiError } from "@/lib/api";
import { translations } from "@/lib/translations";

type Tx = typeof translations["ko"];

interface Props {
  error: ApiError["type"] | null;
  retryLabel: string;
  tx: Pick<Tx, "errorOccurred" | "networkError" | "apiKeyError" | "serverError" | "unknownError" | "loginAgain" | "goHome">;
  onRetry: () => void;
  onHome: () => void;
  onLogout: () => void;
}

export function ApiErrorSheet({ error, retryLabel, tx, onRetry, onHome, onLogout }: Props) {
  if (!error) return null;
  return (
    <div className="fixed inset-0 z-[300] flex flex-col justify-end" style={{ fontFamily: "'Pretendard', -apple-system, BlinkMacSystemFont, system-ui, sans-serif" }}>
      <div className="absolute inset-0 bg-black/50" />
      <div className="relative bg-white rounded-t-[2rem] px-6 pt-8 pb-10" style={{ animation: "slideUp 0.35s cubic-bezier(0.16,1,0.3,1) forwards" }}>
        <style>{`@keyframes slideUp { from { transform: translateY(100%); } to { transform: translateY(0); } }`}</style>
        <div className="w-12 h-12 rounded-full bg-red-50 flex items-center justify-center mx-auto mb-4">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#ef4444" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
        </div>
        <h3 className="text-slate-900 font-bold text-[17px] text-center mb-2">{tx.errorOccurred}</h3>
        <p className="text-slate-500 text-sm text-center mb-7 leading-relaxed whitespace-pre-line">
          {error === "network" ? tx.networkError
            : error === "unauthorized" ? tx.apiKeyError
            : error === "server" ? tx.serverError
            : tx.unknownError}
        </p>
        {error === "unauthorized" ? (
          <button
            onClick={onLogout}
            className="w-full bg-[#006FFF] text-white rounded-[14px] py-3.5 font-semibold text-[14px] active:scale-[0.97] transition-transform"
          >
            {tx.loginAgain}
          </button>
        ) : (
          <div className="flex flex-col gap-3">
            <button
              onClick={onRetry}
              className="w-full bg-[#006FFF] text-white rounded-[14px] py-3.5 font-semibold text-[14px] active:scale-[0.97] transition-transform"
            >
              {retryLabel}
            </button>
            <button
              onClick={onHome}
              className="w-full bg-white border border-[#cbd5e1] text-[#475569] rounded-[14px] py-3.5 font-semibold text-[14px] active:scale-[0.97] transition-transform"
            >
              {tx.goHome}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
