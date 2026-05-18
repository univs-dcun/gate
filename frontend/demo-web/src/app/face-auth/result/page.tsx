"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import confetti from "canvas-confetti";
import { useLanguage } from "@/contexts/LanguageContext";
import { translations } from "@/lib/translations";

function ResultView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const userName = searchParams.get("name") ?? "";
  const mode = searchParams.get("mode") ?? "";
  const type = searchParams.get("type") ?? "";
  const [capturedImage, setCapturedImage] = useState<string | null>(null);
  const [referenceImage, setReferenceImage] = useState<string | null>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const [authResult, setAuthResult] = useState<any>(null);
  const { lang } = useLanguage();
  const tx = translations[lang];

  useEffect(() => {
    if (mode !== "liveness") return;
    const colors = ["#006FFF", "#60a5fa", "#10b981", "#2dd4bf"];
    const opts = { particleCount: 20, spread: 50, ticks: 70, colors };
    setTimeout(() => confetti({ ...opts, angle: 60, origin: { x: 0.1, y: 0.6 } }), 0);
    setTimeout(() => confetti({ ...opts, angle: 120, origin: { x: 0.9, y: 0.6 } }), 200);
  }, [mode]);

  useEffect(() => {
    const img = sessionStorage.getItem("faceAuthCapturedImage");
    if (img) setCapturedImage(img);
    const ref = sessionStorage.getItem("faceAuthReferenceImage");
    if (ref) setReferenceImage(ref);
    try {
      const r = sessionStorage.getItem("univsAuthResult");
      if (r) setAuthResult(JSON.parse(r));
    } catch {}
  }, []);

  const simPct = (s?: number) => s == null ? null : Math.min(Math.round(s <= 1 ? s * 100 : s), 100);

  const fmtSimilarity = (s?: number) => {
    if (s == null) return "—";
    return `${Math.round(s <= 1 ? s * 100 : s)}%`;
  };

  const isPhotoAuth = type === "photo";

  const headerTitle = isPhotoAuth
    ? tx.result11PhotoHeader
    : mode === "1n"
    ? tx.resultMatchingNHeader
    : mode === "liveness"
    ? tx.resultLivenessHeader
    : tx.result11CameraHeader;

  return (
    <div
      className="min-h-screen bg-[#F2F4F5] flex flex-col"
      style={{ fontFamily: "'Pretendard', -apple-system, BlinkMacSystemFont, system-ui, sans-serif" }}
    >
      {/* ── Header ── */}
      <header className="bg-white border-b border-[#e2e8f0] sticky top-0 z-10">
        <div className="w-full h-[60px] flex items-center px-5">
          <div className="flex items-center gap-3">
            <button
              onClick={() => router.back()}
              className="w-6 h-6 flex items-center justify-center"
            >
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                <path d="M15 18l-6-6 6-6" stroke="#64748B" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </button>
            <span className="text-[16px] font-semibold text-[#64748b] tracking-[-0.4px]">
              {headerTitle}
            </span>
          </div>
        </div>
      </header>

      {/* ── Profile / Success Badge ── */}
      <div className="bg-white flex flex-col items-center py-5 gap-3 w-full">
        {isPhotoAuth ? (
          /* 1:1 사진 인증: 두 원형 사진 나란히 */
          <div className="flex items-center gap-3">
            {/* 첨부 사진 */}
            {referenceImage ? (
              <img src={referenceImage} alt="첨부 사진" className="w-24 h-24 rounded-full object-cover" />
            ) : (
              <div className="w-24 h-24 rounded-full bg-[#d1d5db] flex items-center justify-center">
                <span className="text-[#6b7280] text-xs font-semibold">{tx.attachedPhoto}</span>
              </div>
            )}
            {/* 촬영 얼굴 + check badge */}
            <div className="relative w-24 h-24">
              {capturedImage ? (
                <img src={capturedImage} alt="촬영 얼굴" className="w-24 h-24 rounded-full object-cover" />
              ) : (
                <div className="w-24 h-24 rounded-full bg-[#d1d5db] flex items-center justify-center">
                  <span className="text-[#6b7280] text-xs font-semibold">{tx.capturedFace}</span>
                </div>
              )}
              <div className="absolute bottom-0 right-0 w-[24px] h-[24px] rounded-[14px] bg-[#10b981] border border-white flex items-center justify-center">
                <svg width="13" height="10" viewBox="0 0 13 10" fill="none">
                  <path d="M1.5 5l3.5 3.5 6.5-7" stroke="white" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </div>
            </div>
          </div>
        ) : mode === "1n" ? (
          /* 1:N: 두 원형 사진 */
          <div className="flex items-center gap-4">
            {referenceImage ? (
              <img src={referenceImage} alt="기준 사진" className="w-[90px] h-[90px] rounded-full object-cover" />
            ) : (
              <div className="w-[90px] h-[90px] rounded-full bg-[#ccd1d9]" />
            )}
            <div className="relative w-[90px] h-[90px]">
              {capturedImage ? (
                <img src={capturedImage} alt="촬영된 얼굴" className="w-[90px] h-[90px] rounded-full object-cover" />
              ) : (
                <div className="w-[90px] h-[90px] rounded-full bg-[#b0b8c1]" />
              )}
              <div className="absolute bottom-0 right-0 w-[24px] h-[24px] rounded-[14px] bg-[#0fb981] border border-white flex items-center justify-center">
                <svg width="13" height="10" viewBox="0 0 13 10" fill="none">
                  <path d="M1.5 5l3.5 3.5 6.5-7" stroke="white" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </div>
            </div>
          </div>
        ) : (
          /* 단일 원형 (1:1 카메라, 라이브니스) */
          <div className="relative w-[100px] h-[90px]">
            {capturedImage ? (
              <img src={capturedImage} alt="촬영된 얼굴" className="absolute left-[5px] top-0 w-[90px] h-[90px] rounded-full object-cover" />
            ) : (
              <div className="absolute left-[5px] top-0 w-[90px] h-[90px] rounded-full bg-[#ccd1d9]" />
            )}
            <div className="absolute left-[71px] top-[66px] w-[24px] h-[24px] rounded-[14px] bg-[#0fb981] border border-white flex items-center justify-center">
              <svg width="13" height="10" viewBox="0 0 13 10" fill="none">
                <path d="M1.5 5l3.5 3.5 6.5-7" stroke="white" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </div>
          </div>
        )}

        <div className="flex flex-col items-center gap-1">
          <p className="text-[16px] font-semibold text-[#10b981] tracking-[-0.4px] leading-[1.4]">
            {mode === "liveness" ? tx.livenessSuccess : tx.verificationSuccess}
          </p>
          {mode === "liveness" && (
            <p className="text-[14px] font-normal text-[#64748b] leading-[1.4]">{tx.livenessVerifiedDesc}</p>
          )}
          {isPhotoAuth && (
            <p className="text-[14px] font-normal text-[#64748b] leading-[1.4]">{tx.photoFaceMatchDesc}</p>
          )}
        </div>
      </div>

      {/* ── Liveness: 테스트 해보기 카드 ── */}
      {mode === "liveness" && (
        <div className="w-full px-5 pt-6 pb-[100px] bg-[#F2F4F5]">
          <div className="flex items-center gap-1.5 mb-3">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M7.5 1L2 8h5l-.5 5L13 6H8l-.5-5Z" fill="#f59e0b" stroke="#f59e0b" strokeWidth="0.5" strokeLinejoin="round"/>
            </svg>
            <span className="text-[13px] font-semibold text-[#64748b] tracking-[-0.325px]">{tx.tryItOut}</span>
          </div>
          <div className="flex flex-col gap-3">
            {[
              {
                label: tx.cameraVerification,
                desc: tx.realTimeMatchDesc,
                href: "/face-auth",
                bg: "#EFF9FF",
                icon: (
                  <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                    <path d="M2 7.5A1.5 1.5 0 0 1 3.5 6h1.086a1 1 0 0 0 .707-.293l.914-.914A1 1 0 0 1 6.914 4.5h6.172a1 1 0 0 1 .707.293l.914.914A1 1 0 0 0 15.414 6H16.5A1.5 1.5 0 0 1 18 7.5v7A1.5 1.5 0 0 1 16.5 16h-13A1.5 1.5 0 0 1 2 14.5v-7Z" stroke="#006FFF" strokeWidth="1.5"/>
                    <circle cx="10" cy="11" r="2.5" stroke="#006FFF" strokeWidth="1.5"/>
                  </svg>
                ),
              },
              {
                label: tx.photoVerification,
                desc: tx.photoInfoMatchDesc,
                href: "/face-auth/camera?type=photo",
                bg: "#EAFBF9",
                icon: (
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#2dd4bf" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="m2.25 15.75 5.159-5.159a2.25 2.25 0 0 1 3.182 0l5.159 5.159m-1.5-1.5 1.409-1.409a2.25 2.25 0 0 1 3.182 0l2.909 2.909m-18 3.75h16.5a1.5 1.5 0 0 0 1.5-1.5V6a1.5 1.5 0 0 0-1.5-1.5H3.75A1.5 1.5 0 0 0 2.25 6v12a1.5 1.5 0 0 0 1.5 1.5Zm10.5-11.25h.008v.008h-.008V8.25Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Z"/>
                  </svg>
                ),
              },
              {
                label: tx.matchingN,
                desc: tx.multiTargetDesc,
                href: "/face-auth/camera?mode=1n",
                bg: "#F5F2FF",
                icon: (
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#a78bfa" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M18 18.72a9.094 9.094 0 0 0 3.741-.479 3 3 0 0 0-4.682-2.72m.94 3.198.001.031c0 .225-.012.447-.037.666A11.944 11.944 0 0 1 12 21c-2.17 0-4.207-.576-5.963-1.584A6.062 6.062 0 0 1 6 18.719m12 0a5.971 5.971 0 0 0-.941-3.197m0 0A5.995 5.995 0 0 0 12 12.75a5.995 5.995 0 0 0-5.058 2.772m0 0a3 3 0 0 0-4.681 2.72 8.986 8.986 0 0 0 3.74.477m.94-3.197a5.971 5.971 0 0 0-.94 3.197M15 6.75a3 3 0 1 1-6 0 3 3 0 0 1 6 0Zm6 3a2.25 2.25 0 1 1-4.5 0 2.25 2.25 0 0 1 4.5 0Zm-13.5 0a2.25 2.25 0 1 1-4.5 0 2.25 2.25 0 0 1 4.5 0Z"/>
                  </svg>
                ),
              },
            ].map(({ label, desc, href, icon, bg }) => (
              <button
                key={href}
                onClick={() => router.push(href)}
                className="w-full bg-white rounded-[14px] px-4 py-4 flex items-center gap-3 active:scale-[0.98] transition-transform"
                style={{ border: "1px solid #e2e8f0" }}
              >
                <div className="w-9 h-9 rounded-[10px] flex items-center justify-center shrink-0" style={{ backgroundColor: bg ?? "#f8fafc" }}>
                  {icon}
                </div>
                <div className="flex-1 text-left flex flex-col gap-0.5">
                  <p className="text-[14px] font-semibold text-[#1e293b] tracking-[-0.35px] leading-[1.4]">{label}</p>
                  <p className="text-[12px] text-[#64748b] tracking-[-0.3px] leading-[1.4]">{desc}</p>
                </div>
                <svg width="7" height="12" viewBox="0 0 7 12" fill="none">
                  <path d="M1 1l5 5-5 5" stroke="#cbd5e1" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* ── Info Card ── */}
      {mode !== "liveness" && (
        <div className="w-full px-5 pt-4 pb-[100px]">
          <div className="bg-white rounded-[12px] px-5 py-5 flex flex-col gap-5">

            <div className="flex items-center gap-2">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                <path d="M8 1.5L2 4v4c0 3.31 2.57 5.83 6 6.5 3.43-.67 6-3.19 6-6.5V4L8 1.5Z" stroke="#056FFF" strokeWidth="1.3" strokeLinejoin="round"/>
                <path d="M5.5 8l1.8 1.8 3.2-3.2" stroke="#056FFF" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
              <span className="text-[12px] font-semibold text-[#056fff] tracking-[-0.3px]">{tx.registeredInfo}</span>
            </div>

            {isPhotoAuth ? (
              /* 1:1 사진 인증 전용 info */
              <div className="flex flex-col gap-4">
                <div className="flex items-center justify-between">
                  <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.livenessApplied}</span>
                  {authResult?.data?.checkLiveness === true && authResult?.data?.success === true ? (
                    <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{tx.realUserVerified}</span>
                  ) : (
                    <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{tx.livenessNotApplied}</span>
                  )}
                </div>
                <div className="flex flex-col gap-2">
                  <div className="flex items-center justify-between">
                    <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.similarity}</span>
                    <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{fmtSimilarity(authResult?.data?.similarity)}</span>
                  </div>
                  {simPct(authResult?.data?.similarity) != null && (
                    <div className="h-2 bg-[#F2F4F5] rounded-full overflow-hidden">
                      <div
                        className="h-full rounded-full transition-[width] duration-1000 ease-out"
                        style={{
                          width: `${simPct(authResult.data.similarity)}%`,
                          background: (simPct(authResult.data.similarity) ?? 0) >= 80 ? "#0fb981" : (simPct(authResult.data.similarity) ?? 0) >= 60 ? "#f59e0b" : "#ef4444",
                        }}
                      />
                    </div>
                  )}
                </div>
                <div className="h-px bg-[#e2e8f0]" />
                <div className="flex items-center justify-between">
                  <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.logId}</span>
                  <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{authResult?.data?.matchingHistoryId ?? "—"}</span>
                </div>
                <div className="flex flex-col gap-2">
                  <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.requestId}</span>
                  <div className="bg-[#F2F4F5] rounded-[8px] px-3 py-2">
                    <p className="text-[14px] font-semibold text-[#475569] tracking-[-0.35px] leading-[1.4] break-all">
                      {authResult?.data?.transactionUuid ?? "—"}
                    </p>
                  </div>
                </div>
              </div>
            ) : (
              /* 1:1 카메라 / 1:N 인증 info */
              <div className="flex flex-col gap-4">
                <div className="flex items-center justify-between">
                  <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.name}</span>
                  <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{userName}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.livenessApplied}</span>
                  {authResult?.data?.checkLiveness === true && authResult?.data?.success === true ? (
                    <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{tx.realUserVerified}</span>
                  ) : (
                    <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{tx.livenessNotApplied}</span>
                  )}
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.similarity}</span>
                  <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{fmtSimilarity(authResult?.data?.similarity)}</span>
                </div>
                <div className="flex flex-col gap-2">
                  <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.faceId}</span>
                  <div className="bg-[#F2F4F5] rounded-[8px] px-3 py-2">
                    <p className="text-[14px] font-semibold text-[#475569] tracking-[-0.35px] leading-[1.4] break-all">
                      {authResult?.data?.faceId ?? "—"}
                    </p>
                  </div>
                </div>
                <div className="h-px bg-[#e2e8f0]" />
                <div className="flex items-center justify-between">
                  <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.logId}</span>
                  <span className="text-[14px] font-semibold text-[#334155] tracking-[-0.35px] leading-[1.4]">{authResult?.data?.matchingHistoryId ?? "—"}</span>
                </div>
                <div className="flex flex-col gap-2">
                  <span className="text-[13px] font-semibold text-[#94a3b8] tracking-[-0.325px] leading-[1.4]">{tx.requestId}</span>
                  <div className="bg-[#F2F4F5] rounded-[8px] px-3 py-2">
                    <p className="text-[14px] font-semibold text-[#475569] tracking-[-0.35px] leading-[1.4] break-all">
                      {authResult?.data?.transactionUuid ?? "—"}
                    </p>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ── Bottom Buttons ── */}
      <div className="fixed bottom-0 bg-[#F2F4F5] pb-6 pt-3 px-5" style={{ left: "50%", transform: "translateX(-50%)", width: "min(500px, 100vw)", boxSizing: "border-box" }}>
        <div className="flex gap-4">

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
            onClick={() => {
              if (mode === "liveness") {
                router.push("/face-auth/camera?mode=liveness");
              } else if (isPhotoAuth) {
                router.push("/face-auth/camera?type=photo");
              } else if (mode === "1n") {
                router.push("/face-auth/camera?mode=1n");
              } else {
                router.push("/face-auth");
              }
            }}
            className="flex flex-1 items-center justify-center gap-2 bg-[#006FFF] rounded-[14px] py-3.5 active:scale-[0.97] transition-transform"
          >
            {mode !== "liveness" && !isPhotoAuth && mode !== "1n" && (
              <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                <path d="M13.5 6a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0Z" stroke="white" strokeWidth="1.5"/>
                <path d="M2.5 17.5c0-3.314 3.134-6 7-6" stroke="white" strokeWidth="1.5" strokeLinecap="round"/>
                <path d="M16 14v4M14 16h4" stroke="white" strokeWidth="1.5" strokeLinecap="round"/>
              </svg>
            )}
            {(isPhotoAuth || mode === "1n" || mode === "liveness") && (
              <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                <path d="M2 7.5A1.5 1.5 0 0 1 3.5 6h1.086a1 1 0 0 0 .707-.293l.914-.914A1 1 0 0 1 6.914 4.5h6.172a1 1 0 0 1 .707.293l.914.914A1 1 0 0 0 15.414 6H16.5A1.5 1.5 0 0 1 18 7.5v7A1.5 1.5 0 0 1 16.5 16h-13A1.5 1.5 0 0 1 2 14.5v-7Z" stroke="white" strokeWidth="1.5"/>
                <circle cx="10" cy="11" r="2.5" stroke="white" strokeWidth="1.5"/>
              </svg>
            )}
            <span className="text-[14px] font-semibold text-white tracking-[-0.4px] leading-[1.4]">
              {mode === "liveness" ? tx.addLiveness : isPhotoAuth ? tx.addPhotoVerify : mode === "1n" ? tx.addMatchN : tx.addVerify}
            </span>
          </button>

        </div>
      </div>
    </div>
  );
}

export default function FaceAuthResultPage() {
  return (
    <Suspense>
      <ResultView />
    </Suspense>
  );
}
