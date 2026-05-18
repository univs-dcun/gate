"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { useLanguage } from "@/contexts/LanguageContext";
import { translations } from "@/lib/translations";

function ResultView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const mode = searchParams.get("mode") ?? "1n";
  const isLiveness = mode === "liveness";
  const [palmPhoto, setPalmPhoto] = useState<string | null>(null);
  const { lang } = useLanguage();
  const tx = translations[lang];

  useEffect(() => {
    try {
      const photo = sessionStorage.getItem("veinAuthPalmPhoto");
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
            <span className="text-[16px] font-semibold text-[#64748b] tracking-[-0.4px]">
              {isLiveness ? tx.veinAuthLivenessHeader : tx.veinAuth1NHeader}
            </span>
          </div>
        </div>
      </header>

      {/* Profile / Success Badge */}
      <div className="bg-white flex flex-col items-center py-6 gap-3 w-full">
        <div className="relative w-[100px] h-[90px]">
          <div className="absolute left-[5px] top-0 w-[90px] h-[90px] rounded-full bg-[#ccd1d9] overflow-hidden">
            {palmPhoto && (
              <img src={palmPhoto} alt="palm" className="w-full h-full object-cover" />
            )}
          </div>
          <div className="absolute left-[71px] top-[66px] w-[24px] h-[24px] rounded-[14px] bg-[#0fb981] border border-white flex items-center justify-center">
            <svg width="13" height="10" viewBox="0 0 13 10" fill="none">
              <path d="M1.5 5l3.5 3.5 6.5-7" stroke="white" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>
        </div>
        <p className="text-[16px] font-semibold text-[#0fb981] tracking-[-0.4px] leading-[1.4]">
          {isLiveness ? tx.veinLivenessSuccess : tx.matchSuccess}
        </p>
      </div>

      {/* Info Card (라이브니스 모드에서는 숨김) */}
      {!isLiveness && (
        <div className="w-full px-5 pb-[100px] mt-7">
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
                <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.name}</span>
                <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">—</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.veinConfidence}</span>
                <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">—</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Bottom Buttons */}
      <div
        className="fixed bottom-0 bg-[#F2F4F5] pb-6 pt-3 px-5"
        style={{ left: "50%", transform: "translateX(-50%)", width: "min(500px, 100vw)", boxSizing: "border-box" }}
      >
        <div className="flex gap-4">
          <button
            onClick={() => router.push("/vein")}
            className="flex items-center justify-center gap-2 bg-white border border-[#cbd5e1] rounded-[14px] py-4 w-[160px] shrink-0 active:scale-[0.97] transition-transform"
          >
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
              <path d="M3 9.5L10 3l7 6.5V17a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9.5Z" stroke="#475569" strokeWidth="1.5" strokeLinejoin="round"/>
              <path d="M7.5 18V13h5v5" stroke="#475569" strokeWidth="1.5" strokeLinejoin="round"/>
            </svg>
            <span className="text-[16px] font-semibold text-[#475569] tracking-[-0.4px] leading-[1.4]">{tx.goHome}</span>
          </button>

          <button
            onClick={() => router.push(`/vein/auth/camera?mode=${mode}`)}
            className="flex flex-1 items-center justify-center bg-[#006FFF] rounded-[14px] py-4 active:scale-[0.97] transition-transform"
          >
            <span className="text-[16px] font-semibold text-white tracking-[-0.4px] leading-[1.4]">{tx.veinRetryBtn}</span>
          </button>
        </div>
      </div>
    </div>
  );
}

export default function VeinAuthResultPage() {
  return (
    <Suspense>
      <ResultView />
    </Suspense>
  );
}
