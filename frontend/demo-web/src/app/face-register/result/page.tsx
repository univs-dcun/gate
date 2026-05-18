"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { useLanguage } from "@/contexts/LanguageContext";
import { translations } from "@/lib/translations";

type ResultStatus = "success" | "duplicate" | "fail";

function ResultView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const userName = searchParams.get("name") ?? "";
  const status = (searchParams.get("status") ?? "success") as ResultStatus;
  const isDuplicate = status === "duplicate";
  const isFail = status === "fail";
  const [photo, setPhoto] = useState<string | null>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const [regResult, setRegResult] = useState<any>(null);
  const [matchedName, setMatchedName] = useState<string | null>(null);
  const errCode: string = (regResult?.errors?.code ?? "").toUpperCase();
  const isSystemError = isFail && (errCode.startsWith("PJ-") || errCode.startsWith("SYS-") || errCode.startsWith("SERVER"));
  const livenessFailureType = (regResult?.errors?.type ?? regResult?.data?.failureType ?? "").toUpperCase();
  const livenessFailed = isFail && (
    livenessFailureType === "FAKE" ||
    (regResult?.data?.checkLiveness === true && regResult?.data?.success === false)
  );
  const { lang } = useLanguage();
  const tx = translations[lang];

  useEffect(() => {
    const saved = sessionStorage.getItem("faceRegisterPhoto");
    if (saved) setPhoto(saved);
    try {
      const r = sessionStorage.getItem("univsRegisterResult");
      if (r) setRegResult(JSON.parse(r));
    } catch {}
    const mn = sessionStorage.getItem("univsDuplicateMatchedName");
    if (mn) setMatchedName(mn);
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
            <span className="text-[16px] font-semibold text-[#64748b] tracking-[-0.4px]">{tx.newFaceRegResultHeader}</span>
          </div>
        </div>
      </header>

      {/* Profile Area */}
      <div className="bg-white flex flex-col items-center py-5 gap-3 w-full mb-4">
        <div className="relative w-[100px] h-[90px]">
          {photo ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={photo}
              alt="등록된 얼굴"
              className="absolute left-[5px] top-0 w-[90px] h-[90px] rounded-full object-cover"
            />
          ) : (
            <div className="absolute left-[5px] top-0 w-[90px] h-[90px] rounded-full bg-[#ccd1d9]" />
          )}
          <div
            className="absolute left-[71px] top-[66px] w-[24px] h-[24px] rounded-[14px] border border-white flex items-center justify-center"
            style={{ backgroundColor: isDuplicate ? "#f59e0b" : isFail ? "#ef4444" : "#0fb981" }}
          >
            {isDuplicate ? (
              <span className="text-white text-[14px] font-bold leading-none">!</span>
            ) : isFail ? (
              <span className="text-white text-[14px] font-bold leading-none">x</span>
            ) : (
              <svg width="13" height="10" viewBox="0 0 13 10" fill="none">
                <path d="M1.5 5l3.5 3.5 6.5-7" stroke="white" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            )}
          </div>
        </div>

        {isDuplicate ? (
          <div className="flex flex-col items-center gap-2 px-4">
            <p className="text-[16px] font-semibold text-[#ef4444] tracking-[-0.4px] leading-[1.4]">{tx.alreadyRegistered}</p>
            <p className="text-[14px] font-medium text-[#64748b] tracking-[-0.35px] leading-[1.4] text-center">{tx.alreadyInSystem}</p>
          </div>
        ) : isFail ? (
          <div className="flex flex-col items-center gap-1">
            <p className="text-[16px] font-semibold text-[#ef4444] tracking-[-0.4px] leading-[1.4]">
              {livenessFailed && !isSystemError ? tx.livenessFailTitle : tx.regFail}
            </p>
            <p className="text-[14px] font-medium text-[#64748b] tracking-[-0.35px] leading-[1.4] text-center">
              {livenessFailed && !isSystemError
                ? tx.regLivenessFailSubtitle
                : isSystemError
                ? tx.systemErrorApology
                : tx.regFailSubtitle}
            </p>
          </div>
        ) : (
          <p className="text-[16px] font-semibold text-[#0fb981] tracking-[-0.4px] leading-[1.4]">{tx.regSuccess}</p>
        )}
      </div>

      {/* Body */}
      <div className="w-full px-5 pb-[100px] flex flex-col gap-3">
        {isFail ? (
          <>
            {/* Fail Reason Card */}
            <div className="bg-white rounded-[14px] px-5 py-5 flex flex-col gap-3" style={{ border: "1px solid #fee2e2" }}>
              <div className="flex items-center gap-1.5">
                <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
                  <path d="M8 1.5L2 4v4c0 3.31 2.57 5.83 6 6.5 3.43-.67 6-3.19 6-6.5V4L8 1.5Z" fill="#fef2f2" stroke="#ef4444" strokeWidth="1.3" strokeLinejoin="round"/>
                  <path d="M8 5v3.5M8 10.5v.5" stroke="#ef4444" strokeWidth="1.3" strokeLinecap="round"/>
                </svg>
                <span className="text-[11px] font-bold text-[#ef4444] tracking-[0.5px]">{tx.reasonAnalysis}</span>
              </div>
              {livenessFailed && !isSystemError ? (
                <>
                  <div className="flex flex-col gap-1">
                    <p className="text-[15px] font-semibold text-[#1e293b] tracking-[-0.375px] leading-[1.4]">{tx.livenessNotRealFace}</p>
                    <p className="text-[13px] text-[#475569] tracking-[-0.325px] leading-[1.6]">
                      {tx.livenessPleaseTryAgain}
                    </p>
                  </div>
                  <div className="h-px bg-[#e2e8f0]" />
                  <div className="flex items-center justify-between">
                    <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.livenessApplied}</span>
                    <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{tx.livenessAppliedFail}</span>
                  </div>
                  <div className="h-px bg-[#e2e8f0]" />
                  {livenessFailureType ? (
                    <div className="flex items-center justify-between">
                      <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px]">{tx.responseCode}</span>
                      <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px]">{livenessFailureType}</span>
                    </div>
                  ) : null}
                </>
              ) : (
                <div className="flex flex-col gap-2">
                  <p className="text-[15px] font-semibold text-[#1e293b] tracking-[-0.375px] leading-[1.4]">
                    {isSystemError ? tx.systemErrorTitle : tx.faceRegFailed}
                  </p>
                  <div className="flex flex-col gap-1">
                    <p className="text-[13px] text-[#475569] tracking-[-0.325px] leading-[1.6]">
                      {isSystemError ? (regResult?.errors?.message ?? "") : tx.faceRecFailedMsg}
                    </p>
                    {regResult?.errors?.code && (
                      <p className="text-[12px] font-semibold text-[#94a3b8] tracking-[-0.3px]">
                        [{regResult.errors.code}{regResult?.errors?.type ? ` / ${regResult.errors.type}` : ""}]
                      </p>
                    )}
                  </div>
                </div>
              )}
            </div>
          </>
        ) : isDuplicate ? (
          <div className="bg-white rounded-[12px] px-5 py-5 flex flex-col gap-3">
            <div className="flex items-center gap-2">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                <path fillRule="evenodd" clipRule="evenodd" d="M8 1L1.5 4.5V9C1.5 12.5 4.5 15.5 8 16C11.5 15.5 14.5 12.5 14.5 9V4.5L8 1Z" fill="#FEF3C7" stroke="#F59E0B" strokeWidth="1.2" strokeLinejoin="round"/>
                <path d="M8 5.5V9M8 10.5V11" stroke="#F59E0B" strokeWidth="1.4" strokeLinecap="round"/>
              </svg>
              <span className="text-[12px] font-semibold text-[#f59e0b] tracking-[-0.3px]">{tx.matchedInfo}</span>
            </div>
            <div className="flex flex-col gap-2">
              <p className="text-[14px] font-semibold text-[#334155] leading-[24px]">{tx.existingDataFound}</p>
              <p className="text-[13px] text-[#64748b] leading-[20px]">{tx.identicalDataMsg}</p>
            </div>
            <div className="bg-[#F7F9FB] rounded-[12px] px-5 py-4">
              <div className="flex items-center gap-3">
                <div className="bg-white border border-[#e2e8f0] rounded-[8px] p-2 flex items-center justify-center shrink-0">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                    <path fillRule="evenodd" clipRule="evenodd" d="M12 4C9.79086 4 8 5.79086 8 8C8 10.2091 9.79086 12 12 12C14.2091 12 16 10.2091 16 8C16 5.79086 14.2091 4 12 4Z" fill="#94A3B8"/>
                    <path fillRule="evenodd" clipRule="evenodd" d="M4 19C4 15.134 7.58172 12 12 12C16.4183 12 20 15.134 20 19V20H4V19Z" fill="#94A3B8"/>
                  </svg>
                </div>
                <div className="flex flex-col gap-1">
                  <p className="text-[12px] text-[#64748b] leading-[16.8px]">{tx.matchedUser}</p>
                  <p className="text-[14px] font-semibold text-[#334155] leading-[24px]">{matchedName ?? "—"} ({lang === "ko" ? "기존 등록됨" : "Previously Registered"})</p>
                  {regResult?.data?.userDescription && (
                    <p className="text-[13px] text-[#64748b] leading-[20px]">{regResult.data.userDescription}</p>
                  )}
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
                  <path d="M8 1.5L2 4v4c0 3.31 2.57 5.83 6 6.5 3.43-.67 6-3.19 6-6.5V4L8 1.5Z" stroke="#056FFF" strokeWidth="1.3" strokeLinejoin="round"/>
                  <path d="M5.5 8l1.8 1.8 3.2-3.2" stroke="#056FFF" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
                <span className="text-[12px] font-semibold text-[#056fff] tracking-[-0.3px]">{tx.registeredInfo}</span>
              </div>
              <div className="flex flex-col gap-4">
                <div className="flex items-center justify-between">
                  <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.name}</span>
                  <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{userName}</span>
                </div>
                <div className="flex flex-col gap-2">
                  <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.faceId}</span>
                  <div className="bg-[#F2F4F5] rounded-[8px] px-3 py-2">
                    <p className="text-[14px] font-semibold text-[#475569] tracking-[-0.35px] leading-[1.4] break-all">
                      {regResult?.data?.faceId ?? "—"}
                    </p>
                  </div>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.livenessApplied}</span>
                  {regResult?.data?.checkLiveness === true && regResult?.data?.success === true ? (
                    <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{tx.realUserVerified}</span>
                  ) : (
                    <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{tx.livenessNotApplied}</span>
                  )}
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

              {/* 1:1 촬영 인증 */}
              <button
                onClick={() => router.push("/face-auth")}
                className="bg-white border border-[#e2e8f0] rounded-[14px] px-5 py-4 flex items-center gap-3 w-full active:scale-[0.97] transition-transform"
              >
                <div className="flex items-center justify-center shrink-0">
                  <svg width="26" height="26" viewBox="0 0 26 26" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M0 8C0 3.58172 3.58172 0 8 0H18C22.4183 0 26 3.58172 26 8V18C26 22.4183 22.4183 26 18 26H8C3.58172 26 0 22.4183 0 18V8Z" fill="#EFF9FF"/>
                    <path d="M19.104 11.2512C19.104 10.7742 18.791 10.3692 18.3559 10.2546L18.2675 10.2353C18.0175 10.1937 17.7668 10.1555 17.5159 10.1198V10.119C17.2279 10.0792 16.9519 9.97784 16.7074 9.8206C16.4621 9.6628 16.2542 9.45292 16.0984 9.20635L16.0966 9.2046L15.5445 8.31997L15.5428 8.31735C15.4564 8.17724 15.3375 8.05973 15.1962 7.97522C15.0551 7.89082 14.8958 7.84158 14.7316 7.83172V7.83085C13.5778 7.76895 12.4213 7.76892 11.2675 7.83085L11.2684 7.83172C11.1042 7.84158 10.9449 7.89082 10.8038 7.97522C10.6625 8.05973 10.5436 8.17724 10.4573 8.31735L10.4555 8.31997L9.90425 9.2046L9.9025 9.20635C9.74667 9.45299 9.53888 9.66279 9.2935 9.8206C9.04855 9.97807 8.77173 10.0794 8.48325 10.119L8.48413 10.1198L7.7325 10.2353C7.25162 10.3153 6.896 10.7423 6.896 11.2512V16.9133C6.896 17.1955 7.008 17.4663 7.2075 17.6658C7.40704 17.8654 7.67781 17.9773 7.96 17.9773H18.04C18.3222 17.9773 18.593 17.8654 18.7925 17.6658C18.992 17.4663 19.104 17.1955 19.104 16.9133V11.2512ZM20 16.9133C20 17.4331 19.7935 17.9318 19.426 18.2993C19.0584 18.6669 18.5598 18.8733 18.04 18.8733H7.96C7.44018 18.8733 6.94157 18.6669 6.574 18.2993C6.20647 17.9318 6 17.4331 6 16.9133V11.2512C6 10.3261 6.65114 9.50688 7.5855 9.3516C7.84252 9.30865 8.09952 9.26935 8.35812 9.2326L8.36075 9.23172C8.52025 9.20994 8.67333 9.15427 8.80875 9.06722C8.94425 8.98011 9.0587 8.8639 9.14475 8.72772L9.69513 7.84572C9.85681 7.58383 10.0794 7.36505 10.3435 7.20697C10.6082 7.04853 10.907 6.95592 11.215 6.93747H11.2176C12.4048 6.87371 13.5952 6.87371 14.7824 6.93747H14.785C15.093 6.95592 15.3918 7.04853 15.6565 7.20697C15.9206 7.36505 16.1432 7.58383 16.3049 7.84572L16.857 8.72947H16.8561C16.942 8.86502 17.0572 8.97952 17.1921 9.06635C17.3276 9.15347 17.4806 9.21078 17.6401 9.2326H17.6419C17.8998 9.26924 18.1575 9.30878 18.4145 9.3516C19.3479 9.50688 20 10.3259 20 11.2512V16.9133Z" fill="#006FFF"/>
                    <path d="M15.7443 13.6654C15.7443 12.9822 15.4731 12.3267 14.99 11.8436C14.5069 11.3605 13.8515 11.0894 13.1683 11.0894C12.4851 11.0894 11.8296 11.3605 11.3465 11.8436C10.8634 12.3267 10.5923 12.9822 10.5923 13.6654C10.5923 14.3486 10.8634 15.004 11.3465 15.4871C11.8296 15.9702 12.4851 16.2414 13.1683 16.2414C13.8515 16.2414 14.5069 15.9702 14.99 15.4871C15.4731 15.004 15.7443 14.3486 15.7443 13.6654ZM17.7095 11.7054C17.957 11.7054 18.1575 11.9059 18.1575 12.1534V12.1586C18.1575 12.406 17.957 12.6066 17.7095 12.6066H17.7043C17.4569 12.6066 17.2563 12.406 17.2563 12.1586V12.1534C17.2563 11.9059 17.4569 11.7054 17.7043 11.7054H17.7095ZM16.6403 13.6654C16.6403 14.5862 16.2747 15.4695 15.6235 16.1206C14.9724 16.7717 14.0891 17.1374 13.1683 17.1374C12.2475 17.1374 11.3642 16.7717 10.713 16.1206C10.0619 15.4695 9.69629 14.5862 9.69629 13.6654C9.69629 12.7445 10.0619 11.8612 10.713 11.2101C11.3642 10.559 12.2475 10.1934 13.1683 10.1934C14.0891 10.1934 14.9724 10.559 15.6235 11.2101C16.2747 11.8612 16.6403 12.7445 16.6403 13.6654Z" fill="#006FFF"/>
                  </svg>
                </div>
                <div className="flex flex-1 items-center gap-3 min-w-0">
                  <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] shrink-0">{tx.cameraVerification}</span>
                  <span className="text-[13px] text-[#94a3b8] tracking-[-0.325px] truncate">{tx.realTimeMatchDesc}</span>
                </div>
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" className="shrink-0">
                  <path d="M9 18l6-6-6-6" stroke="#CBD5E1" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </button>

              {/* 1:N 매칭 */}
              <button onClick={() => router.push("/face-auth/camera?mode=1n")} className="bg-white border border-[#e2e8f0] rounded-[14px] px-5 py-4 flex items-center gap-3 w-full active:scale-[0.97] transition-transform">
                <div className="bg-[#EBF0FE] p-[6px] rounded-[8px] flex items-center justify-center shrink-0">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                    <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" stroke="#4F6FF5" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    <circle cx="9" cy="7" r="4" stroke="#4F6FF5" strokeWidth="2"/>
                    <path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" stroke="#4F6FF5" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                </div>
                <div className="flex flex-1 items-center gap-3 min-w-0">
                  <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] shrink-0">{tx.matchingN}</span>
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
          {isFail ? (
            <>
              <button
                onClick={() => router.push("/")}
                className="flex items-center justify-center gap-2 bg-white border border-[#cbd5e1] rounded-[14px] py-3.5 w-[160px] shrink-0 active:scale-[0.97] transition-transform"
              >
                <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                  <path d="M3 9.5L10 3l7 6.5V17a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9.5Z" stroke="#475569" strokeWidth="1.5" strokeLinejoin="round"/>
                  <path d="M7.5 18V13h5v5" stroke="#475569" strokeWidth="1.5" strokeLinejoin="round"/>
                </svg>
                <span className="text-[14px] font-semibold text-[#475569] tracking-[-0.4px] leading-[1.4]">{tx.goHome}</span>
              </button>
              <button
                onClick={() => router.push("/face-register")}
                className="flex flex-1 items-center justify-center gap-2 rounded-[14px] py-3.5 active:scale-[0.97] transition-transform"
                style={{ backgroundColor: "#006FFF" }}
              >
                <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                  <path d="M15 10A5 5 0 1 1 10 5" stroke="white" strokeWidth="1.5" strokeLinecap="round"/>
                  <path d="M10 2v4l2.5-2L10 2Z" fill="white"/>
                </svg>
                <span className="text-[14px] font-semibold text-white tracking-[-0.4px] leading-[1.4]">{tx.tryAgain}</span>
              </button>
            </>
          ) : isDuplicate ? (
            <>
              <button
                onClick={() => router.push("/face-register")}
                className="flex items-center justify-center gap-2 bg-white border border-[#cbd5e1] rounded-[14px] py-3.5 w-[160px] shrink-0 active:scale-[0.97] transition-transform"
              >
                <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                  <path d="M13.5 6a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0Z" stroke="#475569" strokeWidth="1.5"/>
                  <path d="M2.5 17.5c0-3.314 3.134-6 7-6" stroke="#475569" strokeWidth="1.5" strokeLinecap="round"/>
                  <path d="M16 14v4M14 16h4" stroke="#475569" strokeWidth="1.5" strokeLinecap="round"/>
                </svg>
                <span className="text-[14px] font-semibold text-[#475569] tracking-[-0.4px] leading-[1.4]">{tx.registerAnother}</span>
              </button>
              <button
                onClick={() => router.push("/")}
                className="flex flex-1 items-center justify-center gap-2 bg-[#006FFF] rounded-[14px] py-3.5 active:scale-[0.97] transition-transform"
              >
                <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                  <path fillRule="evenodd" clipRule="evenodd" d="M10 2.5L2 9.5V18H7.5V13H12.5V18H18V9.5L10 2.5Z" fill="white"/>
                  <path d="M2 9.5L10 2.5L18 9.5V18H12.5V13H7.5V18H2V9.5Z" stroke="white" strokeWidth="1.5" strokeLinejoin="round"/>
                </svg>
                <span className="text-[14px] font-semibold text-white tracking-[-0.4px] leading-[1.4]">{tx.goHome}</span>
              </button>
            </>
          ) : (
            <>
              <button
                onClick={() => router.push("/")}
                className="flex items-center justify-center gap-2 bg-white border border-[#cbd5e1] rounded-[14px] py-3.5 w-[160px] shrink-0 active:scale-[0.97] transition-transform"
              >
                <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                  <path d="M3 9.5L10 3l7 6.5V17a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9.5Z" stroke="#475569" strokeWidth="1.5" strokeLinejoin="round"/>
                  <path d="M7.5 18V13h5v5" stroke="#475569" strokeWidth="1.5" strokeLinejoin="round"/>
                </svg>
                <span className="text-[14px] font-semibold text-[#475569] tracking-[-0.4px] leading-[1.4]">{tx.goHome}</span>
              </button>
              <button
                onClick={() => router.push("/face-register")}
                className="flex flex-1 items-center justify-center gap-2 bg-[#006FFF] rounded-[14px] py-3.5 active:scale-[0.97] transition-transform"
              >
                <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                  <path d="M13.5 6a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0Z" stroke="white" strokeWidth="1.5"/>
                  <path d="M2.5 17.5c0-3.314 3.134-6 7-6" stroke="white" strokeWidth="1.5" strokeLinecap="round"/>
                  <path d="M16 14v4M14 16h4" stroke="white" strokeWidth="1.5" strokeLinecap="round"/>
                </svg>
                <span className="text-[14px] font-semibold text-white tracking-[-0.4px] leading-[1.4]">{tx.registerAnother}</span>
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

export default function FaceRegisterResultPage() {
  return (
    <Suspense>
      <ResultView />
    </Suspense>
  );
}
