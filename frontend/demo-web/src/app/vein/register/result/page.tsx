"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { useLanguage } from "@/contexts/LanguageContext";
import { translations } from "@/lib/translations";

type ResultStatus = "success" | "duplicate";

function ResultView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const userName = searchParams.get("name") ?? "";
  const status = (searchParams.get("status") ?? "success") as ResultStatus;
  const isDuplicate = status === "duplicate";
  const [palmPhoto, setPalmPhoto] = useState<string | null>(null);
  const { lang } = useLanguage();
  const tx = translations[lang];

  useEffect(() => {
    try {
      const photo = sessionStorage.getItem("veinPalmPhoto");
      if (photo) setPalmPhoto(photo);
    } catch {}
  }, []);

  return (
    <div
      className="min-h-screen bg-[#F2F4F5] flex flex-col"
      style={{ fontFamily: "'Pretendard', -apple-system, BlinkMacSystemFont, system-ui, sans-serif" }}
    >
      {/* Header */}
      <header className="bg-white border-b border-[#e2e8f0] sticky top-0 z-10">
        <div className="w-full h-[60px] flex items-center px-5">
          <div className="flex items-center gap-3">
            <button onClick={() => router.back()} className="w-6 h-6 flex items-center justify-center">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                <path d="M15 18l-6-6 6-6" stroke="#64748B" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </button>
            <span className="text-[16px] font-semibold text-[#64748b] tracking-[-0.4px]">{tx.veinRegPageHeader}</span>
          </div>
        </div>
      </header>

      {/* Profile Area */}
      <div className="bg-white flex flex-col items-center py-6 gap-3 w-full">
        <div className="relative w-[100px] h-[90px]">
          <div className="absolute left-[5px] top-0 w-[90px] h-[90px] rounded-full bg-[#ccd1d9] overflow-hidden">
            {palmPhoto && (
              <img src={palmPhoto} alt="palm" className="w-full h-full object-cover" />
            )}
          </div>
          <div
            className="absolute left-[71px] top-[66px] w-[24px] h-[24px] rounded-[14px] border border-white flex items-center justify-center"
            style={{ backgroundColor: isDuplicate ? "#f59e0b" : "#0fb981" }}
          >
            {isDuplicate ? (
              <span className="text-white text-[14px] font-bold leading-none">!</span>
            ) : (
              <svg width="13" height="10" viewBox="0 0 13 10" fill="none">
                <path d="M1.5 5l3.5 3.5 6.5-7" stroke="white" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            )}
          </div>
        </div>

        {isDuplicate ? (
          <div className="flex flex-col items-center gap-2 px-4">
            <p className="text-[16px] font-semibold text-[#334155] tracking-[-0.4px] leading-[1.4]">{tx.alreadyRegistered}</p>
            <p className="text-[14px] font-medium text-[#94a3b8] tracking-[-0.35px] leading-[1.4] text-center">{tx.alreadyInSystem}</p>
          </div>
        ) : (
          <p className="text-[16px] font-semibold text-[#0fb981] tracking-[-0.4px] leading-[1.4]">{tx.veinRegDone}</p>
        )}
      </div>

      {/* Body */}
      <div className="w-full px-5 pb-[100px] flex flex-col gap-3 mt-7">
        {isDuplicate ? (
          <div className="bg-white rounded-[12px] px-5 py-5 flex flex-col gap-4">
            <div className="flex items-center gap-2">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                <path d="M8 1.5L2 4v4c0 3.31 2.57 5.83 6 6.5 3.43-.67 6-3.19 6-6.5V4L8 1.5Z" stroke="#F59E0B" strokeWidth="1.3" strokeLinejoin="round"/>
                <path d="M5.5 8l1.8 1.8 3.2-3.2" stroke="#F59E0B" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
              <span className="text-[12px] font-semibold text-[#f59e0b] tracking-[-0.3px]">MATCHED INFO</span>
            </div>
            <div className="flex flex-col gap-2">
              <p className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{tx.existingDataFound}</p>
              <p className="text-[13px] text-[#64748b] tracking-[-0.325px] leading-[1.4]">{tx.veinIdenticalMsg}</p>
            </div>
            <div className="bg-[#F2F4F5] rounded-[12px] flex items-center px-5 py-4">
              <div className="flex items-center gap-3 w-full">
                <div className="bg-white border border-[#e2e8f0] rounded-[8px] p-2 flex items-center justify-center shrink-0">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" stroke="#94A3B8" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                    <circle cx="12" cy="7" r="4" stroke="#94A3B8" strokeWidth="1.5"/>
                  </svg>
                </div>
                <div className="flex flex-col gap-1">
                  <p className="text-[12px] text-[#64748b] tracking-[-0.3px] leading-[1.4]">{tx.matchedUser}</p>
                  <p className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{userName} {tx.veinPrevRegistered}</p>
                </div>
              </div>
            </div>
          </div>
        ) : (
          <>
            {/* Success Info Card */}
            <div className="bg-white rounded-[12px] px-5 py-5 flex flex-col gap-4">
              <div className="flex items-center gap-2">
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                  <path d="M8 1.5L2 4v4c0 3.31 2.57 5.83 6 6.5 3.43-.67 6-3.19 6-6.5V4L8 1.5Z" stroke="#64748B" strokeWidth="1.3" strokeLinejoin="round"/>
                  <path d="M5.5 8l1.8 1.8 3.2-3.2" stroke="#64748B" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
                <span className="text-[12px] font-semibold text-[#64748b] tracking-[-0.3px]">REGISTERED INFO</span>
              </div>
              <div className="flex flex-col gap-4">
                <div className="flex items-center justify-between">
                  <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.veinMemo}</span>
                  <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{userName}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.veinConfidence}</span>
                  <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">67%</span>
                </div>
              </div>
            </div>

            {/* Test section */}
            <div className="flex flex-col gap-2">
              <div className="flex items-center gap-1.5 px-1 py-1">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
                  <path d="M12 3l1.912 5.813a2 2 0 0 0 1.275 1.275L21 12l-5.813 1.912a2 2 0 0 0-1.275 1.275L12 21l-1.912-5.813a2 2 0 0 0-1.275-1.275L3 12l5.813-1.912a2 2 0 0 0 1.275-1.275L12 3z" stroke="#4BBBFB" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
                <span className="text-[14px] font-medium text-[#4bbbfb] tracking-[-0.35px]">{tx.tryItOut}</span>
              </div>

              <button
                onClick={() => router.push("/vein")}
                className="bg-white border border-[#e2e8f0] rounded-[12px] px-5 py-3 flex items-center gap-3 w-full active:scale-[0.97] transition-transform"
              >
                <div className="bg-[#EAFBF9] p-[6px] rounded-[8px] flex items-center justify-center shrink-0">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                    <path d="M18 11V8a6 6 0 0 0-12 0v3" stroke="#0d9488" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <path d="M5 11h14v10a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V11Z" stroke="#0d9488" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                </div>
                <div className="flex flex-1 items-center gap-3 min-w-0">
                  <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] shrink-0">{tx.veinMatchingNTitle}</span>
                  <span className="text-[13px] text-[#94a3b8] tracking-[-0.325px] truncate">{tx.multiTargetDesc}</span>
                </div>
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" className="shrink-0">
                  <path d="M9 18l6-6-6-6" stroke="#CBD5E1" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </button>
            </div>
          </>
        )}
      </div>

      {/* Bottom Buttons */}
      <div className="fixed bottom-0 bg-[#F2F4F5] pb-6 pt-3 px-5" style={{ left: "50%", transform: "translateX(-50%)", width: "min(500px, 100vw)", boxSizing: "border-box" }}>
        <div className="flex gap-4">
          {isDuplicate ? (
            <>
              <button
                onClick={() => router.push("/vein/register")}
                className="flex items-center justify-center gap-2 bg-white border border-[#cbd5e1] rounded-[14px] py-4 w-[160px] shrink-0 active:scale-[0.97] transition-transform"
              >
                <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                  <path d="M13.5 6a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0Z" stroke="#475569" strokeWidth="1.5"/>
                  <path d="M2.5 17.5c0-3.314 3.134-6 7-6" stroke="#475569" strokeWidth="1.5" strokeLinecap="round"/>
                  <path d="M16 14v4M14 16h4" stroke="#475569" strokeWidth="1.5" strokeLinecap="round"/>
                </svg>
                <span className="text-[16px] font-semibold text-[#475569] tracking-[-0.4px] leading-[1.4]">{tx.registerAnother}</span>
              </button>
              <button
                onClick={() => router.push("/")}
                className="flex flex-1 items-center justify-center gap-2 bg-[#006FFF] rounded-[14px] py-4 active:scale-[0.97] transition-transform"
              >
                <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                  <path d="M3 9.5L10 3l7 6.5V17a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9.5Z" stroke="white" strokeWidth="1.5" strokeLinejoin="round"/>
                  <path d="M7.5 18V13h5v5" stroke="white" strokeWidth="1.5" strokeLinejoin="round"/>
                </svg>
                <span className="text-[16px] font-semibold text-white tracking-[-0.4px] leading-[1.4]">{tx.goHome}</span>
              </button>
            </>
          ) : (
            <>
              <button
                onClick={() => router.push("/")}
                className="flex items-center justify-center gap-2 bg-white border border-[#cbd5e1] rounded-[14px] py-4 w-[160px] shrink-0 active:scale-[0.97] transition-transform"
              >
                <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                  <path d="M3 9.5L10 3l7 6.5V17a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9.5Z" stroke="#475569" strokeWidth="1.5" strokeLinejoin="round"/>
                  <path d="M7.5 18V13h5v5" stroke="#475569" strokeWidth="1.5" strokeLinejoin="round"/>
                </svg>
                <span className="text-[16px] font-semibold text-[#475569] tracking-[-0.4px] leading-[1.4]">{tx.goHome}</span>
              </button>
              <button
                onClick={() => router.push("/vein/register")}
                className="flex flex-1 items-center justify-center gap-2 bg-[#006FFF] rounded-[14px] py-4 active:scale-[0.97] transition-transform"
              >
                <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                  <path d="M13.5 6a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0Z" stroke="white" strokeWidth="1.5"/>
                  <path d="M2.5 17.5c0-3.314 3.134-6 7-6" stroke="white" strokeWidth="1.5" strokeLinecap="round"/>
                  <path d="M16 14v4M14 16h4" stroke="white" strokeWidth="1.5" strokeLinecap="round"/>
                </svg>
                <span className="text-[16px] font-semibold text-white tracking-[-0.4px] leading-[1.4]">{tx.registerAnother}</span>
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

export default function VeinRegisterResultPage() {
  return (
    <Suspense>
      <ResultView />
    </Suspense>
  );
}
