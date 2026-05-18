"use client";

import { useState, useEffect, useRef, useCallback, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { CheckCircle2, Hand, CameraOff } from "lucide-react";
import { useLanguage } from "@/contexts/LanguageContext";
import { translations } from "@/lib/translations";

type ScanStatus = "idle" | "ready" | "detecting" | "scanning" | "complete";

const GlobalStyle = () => (
  <style dangerouslySetInnerHTML={{ __html: `
    @keyframes pulsePalmGuide {
      0%, 100% { opacity: 0.35; transform: scale(1); }
      50% { opacity: 0.55; transform: scale(1.02); }
    }
    @keyframes palmDetecting {
      0%, 100% { opacity: 0.75; transform: scale(1); }
      50% { opacity: 1; transform: scale(1.04); }
    }
    @keyframes palmScanMove {
      0% { top: 38%; opacity: 0; }
      15% { opacity: 1; }
      85% { opacity: 1; }
      100% { top: 74%; opacity: 0; }
    }
    @keyframes checkPop {
      0% { transform: scale(0.5); opacity: 0; }
      70% { transform: scale(1.1); opacity: 1; }
      100% { transform: scale(1); opacity: 1; }
    }
    @keyframes retryIn {
      from { transform: translateY(15px); opacity: 0; }
      to { transform: translateY(0); opacity: 1; }
    }
    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(10px); }
      to { opacity: 1; transform: translateY(0); }
    }
    @keyframes pulseRing {
      0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(45,212,191,0.5); }
      70% { transform: scale(1); box-shadow: 0 0 0 14px rgba(45,212,191,0); }
      100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(45,212,191,0); }
    }
    .animate-palm-guide { animation: pulsePalmGuide 2.5s infinite ease-in-out; }
    .animate-palm-detecting { animation: palmDetecting 0.7s infinite ease-in-out; }
    .animate-palm-scan { animation: palmScanMove 2.5s infinite linear; }
    .animate-check-pop { animation: checkPop 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) forwards; }
    .animate-retry-in { animation: retryIn 0.4s ease-out forwards; }
    .animate-fade-in { animation: fadeIn 0.4s ease-out forwards; }
    .animate-pulse-ring { animation: pulseRing 1.2s infinite; }
  `}} />
);

function CameraView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const mode = searchParams.get("mode") ?? "1n";
  const isLiveness = mode === "liveness";
  const { lang } = useLanguage();
  const tx = translations[lang];

  const [scanStatus, setScanStatus] = useState<ScanStatus>("idle");
  const [cameraError, setCameraError] = useState(false);
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);

  const startCamera = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: "environment" }, width: { ideal: 1280 }, height: { ideal: 720 } },
      });
      streamRef.current = stream;
      if (videoRef.current) videoRef.current.srcObject = stream;
      setScanStatus("ready");
      setCameraError(false);
    } catch {
      setCameraError(true);
    }
  }, []);

  useEffect(() => {
    startCamera();
    return () => { streamRef.current?.getTracks().forEach((t) => t.stop()); };
  }, [startCamera]);

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    if (scanStatus === "ready") {
      timer = setTimeout(() => setScanStatus("detecting"), 2500);
    } else if (scanStatus === "detecting") {
      timer = setTimeout(() => setScanStatus("scanning"), 800);
    } else if (scanStatus === "scanning") {
      timer = setTimeout(() => {
        const video = videoRef.current;
        const canvas = canvasRef.current;
        if (video && canvas && video.readyState >= 2) {
          canvas.width = video.videoWidth || 320;
          canvas.height = video.videoHeight || 240;
          const ctx = canvas.getContext("2d");
          if (ctx) {
            ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
            try { sessionStorage.setItem("veinAuthPalmPhoto", canvas.toDataURL("image/jpeg", 0.8)); } catch {}
          }
        }
        setScanStatus("complete");
      }, 3500);
    } else if (scanStatus === "complete") {
      timer = setTimeout(() => {
        streamRef.current?.getTracks().forEach((t) => t.stop());
        router.replace(`/vein/auth/result?mode=${mode}`);
      }, 1500);
    }
    return () => clearTimeout(timer);
  }, [scanStatus, router, mode]);

  const getPalmClass = () => {
    if (scanStatus === "detecting") return "animate-palm-detecting";
    if (scanStatus === "complete") return "scale-100 opacity-20 grayscale";
    return "animate-palm-guide";
  };

  const getGuideMessage = () => {
    switch (scanStatus) {
      case "idle":      return <>{tx.veinCameraInit}</>;
      case "ready":     return <><span className="text-[#0D9488]">{tx.veinBringPalmBold}</span>{tx.veinBringPalmSuffix}</>;
      case "detecting": return <><span className="text-[#0D9488]">{tx.veinPalmDetectedBold}</span>{tx.veinPalmDetectedSuffix}</>;
      case "scanning":  return isLiveness
        ? <><span className="text-[#0D9488]">{tx.veinLivenessAnalyzingBold}</span>{tx.veinExtractingSuffix}</>
        : <><span className="text-[#0D9488]">{tx.veinExtractingBold}</span>{tx.veinExtractingSuffix}</>;
      case "complete":  return <><span className="text-emerald-600 font-bold">{tx.veinRecognizedBold}</span>{tx.veinRecognizedAuthSuffix}</>;
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col font-medium text-slate-800 animate-fade-in">
      <GlobalStyle />
      <canvas ref={canvasRef} className="hidden" />

      <header className="px-4 h-16 flex items-center justify-between sticky top-0 z-40 bg-white border-b border-slate-100">
        <button
          onClick={() => router.push("/")}
          className="w-10 h-10 bg-slate-100 rounded-xl flex items-center justify-center active:scale-90 transition-all"
        >
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <path d="M15 18l-6-6 6-6" stroke="#475569" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </button>
        <h2 className="font-semibold text-lg tracking-tight text-slate-900">
          {isLiveness ? tx.veinAuthLivenessHeader : tx.veinAuth1NHeader}
        </h2>
        <div className="w-10" />
      </header>

      <main className="flex-1 p-4 flex flex-col gap-4 pb-24 max-w-md mx-auto w-full">

        {/* 가이드 팁 */}
        <div className="bg-[#F0FDFA] border border-[#CCFBF1] p-3.5 rounded-2xl flex gap-3 items-center shadow-sm min-h-[56px] transition-all duration-300">
          <div className="w-8 h-8 bg-white rounded-lg flex items-center justify-center shrink-0 border border-[#99F6E4]">
            {scanStatus === "complete"
              ? <CheckCircle2 className="w-4 h-4 text-emerald-500" />
              : <Hand className="w-4 h-4 text-[#14b8a6]" />}
          </div>
          <p className="text-[12px] text-[#134E4A] leading-tight font-semibold">
            {getGuideMessage()}
          </p>
        </div>

        {/* 카메라 뷰포트 */}
        <section className="w-full">
          <div className="relative bg-[#020617] rounded-[3rem] overflow-hidden shadow-2xl border-4 border-white aspect-[4/5]">

            <video
              ref={videoRef}
              autoPlay
              playsInline
              muted
              className="absolute inset-0 w-full h-full object-cover"
            />

            <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,rgba(45,212,191,0.07)_0%,rgba(2,6,23,0.45)_100%)] pointer-events-none" />

            {cameraError ? (
              <div className="absolute inset-0 flex flex-col items-center justify-center gap-4 z-10">
                <CameraOff className="w-12 h-12 text-white/40" />
                <p className="text-white/60 text-sm font-medium text-center px-8">{tx.veinCameraDenied}</p>
                <button
                  onClick={startCamera}
                  className="bg-white/10 text-white px-6 py-3 rounded-2xl text-sm font-bold border border-white/20"
                >
                  {tx.veinCameraRetry}
                </button>
              </div>
            ) : (
              <div className="relative w-full h-full flex items-center justify-center">

                {/* 손바닥 가이드 SVG */}
                <div className={`absolute flex items-center justify-center -translate-y-8 transition-all duration-700 ${getPalmClass()}`}>
                  {scanStatus === "detecting" && (
                    <div className="absolute w-48 h-48 rounded-full border-2 border-[#2DD4BF] animate-pulse-ring" />
                  )}
                  <svg width="300" height="340" viewBox="0 0 293 328" fill="none" xmlns="http://www.w3.org/2000/svg" className="drop-shadow-[0_0_20px_rgba(45,212,191,0.5)]">
                    <path d="M257.649 99.4832C253.249 99.4892 248.968 100.677 245.444 102.87C230.684 112.052 218.435 123.737 209.507 137.152L202.857 147.125V125.907L208.299 62.5335C208.856 56.0357 209.138 49.4545 209.138 42.9827C209.145 38.8399 207.274 34.8473 203.897 31.8032C200.52 28.7591 195.887 26.8878 190.923 26.5632C185.96 26.2387 181.033 27.4849 177.126 30.0527C173.219 32.6206 170.621 36.3208 169.851 40.4138L160.971 87.2066L153.732 15.2092C153.313 11.0826 150.994 7.25045 147.26 4.51235C143.526 1.77426 138.666 0.341792 133.694 0.51389C128.721 0.685988 124.021 2.44935 120.573 5.436C117.126 8.42265 115.197 12.4018 115.19 16.5431V92.5216L99.0723 44.2489C97.7439 40.2795 94.7333 36.8445 90.5972 34.5789C86.4612 32.3134 81.4793 31.3705 76.5726 31.9245C71.6659 32.4786 67.1661 34.4922 63.9052 37.593C60.6442 40.6938 58.8426 44.6722 58.8333 48.7927C58.8332 55.1247 59.5841 61.4413 61.0751 67.6505L71.8831 112.614L48.8644 85.2526C46.5958 82.5516 43.351 80.5232 39.6018 79.4623C35.8526 78.4014 31.7945 78.3633 28.0175 79.3536C24.2405 80.344 20.9416 82.311 18.6005 84.9688C16.2594 87.6265 14.9981 90.8365 15 94.1318C15.0033 98.5056 16.3069 102.811 18.7947 106.664L58.8333 168.537V194.262C58.8554 221.121 69.9017 247.168 90.1429 268.089V327.5L102.667 327.5V266.228C102.665 265.009 102.154 263.828 101.22 262.888C81.9388 243.618 71.3733 219.338 71.3571 194.262V167.223C71.3562 166.362 71.1003 165.513 70.612 164.754L29.8219 101.703C28.3197 99.3748 27.5304 96.7743 27.5238 94.1318C27.524 93.0536 27.9372 92.0035 28.7034 91.1341C29.4697 90.2647 30.549 89.6212 31.7847 89.297C33.0204 88.9728 34.3482 88.9849 35.5751 89.3314C36.802 89.678 37.8642 90.341 38.6074 91.2241L78.7525 138.945C79.5766 139.925 80.7809 140.639 82.1631 140.967C83.5453 141.296 85.0213 141.219 86.3432 140.749C87.6651 140.279 88.7524 139.445 89.4227 138.387C90.0929 137.328 90.3052 136.11 90.0239 134.937L73.3547 65.6131C72.0253 60.0747 71.3562 54.4406 71.3571 48.7927C71.3443 47.182 72.0367 45.6225 73.305 44.4057C74.5734 43.1889 76.3307 42.3981 78.2487 42.1811C80.1667 41.964 82.1141 42.3356 83.7268 43.2263C85.3396 44.117 86.5074 45.466 87.0119 47.021L115.428 132.144C115.84 133.352 116.762 134.395 118.025 135.083C119.287 135.77 120.805 136.055 122.3 135.885C123.795 135.715 125.165 135.102 126.16 134.158C127.155 133.214 127.707 132.003 127.714 130.748V16.5431C127.698 15.0781 128.367 13.6651 129.58 12.6031C130.793 11.541 132.455 10.9131 134.214 10.8522C135.973 10.7913 137.692 11.3021 139.006 12.2767C140.321 13.2512 141.129 14.613 141.259 16.0742L152.762 131.18C152.889 132.449 153.571 133.635 154.677 134.515C155.783 135.394 157.238 135.906 158.767 135.954C160.311 136.055 161.843 135.654 163.039 134.835C164.235 134.015 165.002 132.842 165.179 131.561L182.193 42.0395C182.476 40.5394 183.429 39.1834 184.861 38.2425C186.293 37.3016 188.099 36.8451 189.919 36.9642C191.738 37.0834 193.436 37.7693 194.674 38.8851C195.912 40.0009 196.598 41.4642 196.595 42.9827C196.595 49.2356 196.326 55.5407 195.794 61.7988L190.333 125.537V167.223C190.335 168.377 190.796 169.498 191.645 170.41C192.495 171.322 193.684 171.974 195.026 172.263C196.369 172.552 197.789 172.462 199.063 172.008C200.337 171.553 201.394 170.759 202.068 169.751L220.453 142.212C228.526 130.079 239.603 119.511 252.952 111.208C254.115 110.48 255.498 110.036 256.946 109.926C258.395 109.817 259.852 110.045 261.153 110.587C262.454 111.128 263.548 111.961 264.311 112.991C265.075 114.021 265.479 115.208 265.476 116.418C265.469 121.604 264.018 126.717 261.237 131.358C251.694 147.209 245.814 164.411 243.904 182.069C241.815 201.544 232.454 219.987 217.134 234.813L204.366 247.209C203.391 248.151 202.855 249.353 202.857 250.596V327.5L215.381 327.5V252.524L226.652 241.581C243.666 225.119 254.06 204.639 256.378 183.012C258.144 166.649 263.593 150.708 272.439 136.021C276.087 129.932 277.99 123.222 278 116.418C277.993 111.929 275.847 107.624 272.032 104.449C268.217 101.275 263.044 99.4887 257.649 99.4832Z" fill="white" fillOpacity={scanStatus === "complete" ? 0.6 : 0.2} />
                    <path d="M153.235 197.603C162.356 196.981 169.037 199.991 173.588 204.081C178.049 208.091 180.394 213.062 181.213 216.483C181.492 217.648 180.659 218.738 179.351 218.917C178.043 219.096 176.756 218.298 176.477 217.133C175.82 214.387 173.867 210.252 170.227 206.98C166.825 203.923 161.885 201.555 154.822 201.816C151.526 205.414 148.253 211.464 148.539 218.561C148.573 219.412 148.661 220.282 148.805 221.169C163.429 219.778 172.189 225.696 175.004 229.647C175.732 230.671 175.413 231.975 174.293 232.56C173.172 233.145 171.671 232.789 170.942 231.766C169.3 229.462 162.628 224.268 149.918 225.383C152.03 231.138 156.803 237.403 165.981 243.601C167.072 244.338 167.328 245.682 166.554 246.603C165.78 247.523 164.27 247.672 163.18 246.937C152.349 239.623 146.836 231.931 144.726 224.696C144.557 224.471 144.43 224.219 144.36 223.945C144.293 223.679 144.286 223.418 144.327 223.169C143.963 221.574 143.76 220.004 143.698 218.47C143.343 209.679 147.622 202.366 151.702 198.272L152.305 197.666L153.235 197.603ZM121.699 225.121C123.547 224.036 125.847 223.607 128.365 224.477C131.032 225.398 132.898 227.001 134.329 228.781C135.715 230.505 136.791 232.528 137.784 234.332C139.867 238.117 141.805 241.302 145.871 243.048C147.099 243.575 147.668 244.857 147.14 245.911C146.612 246.965 145.188 247.393 143.96 246.867C138.079 244.343 135.477 239.699 133.449 236.014C132.386 234.082 131.513 232.457 130.433 231.113C129.398 229.825 128.28 228.942 126.827 228.44C126.048 228.171 125.358 228.237 124.539 228.718C123.622 229.255 122.656 230.268 121.727 231.713C119.882 234.584 118.602 238.477 118.007 241.205C117.758 242.348 116.491 243.044 115.177 242.759C113.864 242.473 113 241.313 113.249 240.169C113.896 237.205 115.303 232.842 117.482 229.451C118.565 227.767 119.947 226.148 121.699 225.121ZM119.622 162.994C120.078 161.911 121.469 161.41 122.726 161.874C125.238 162.803 128.148 165.114 130.839 167.853C133.611 170.675 136.432 174.244 138.71 178.045C140.98 181.834 142.776 185.964 143.38 189.886C143.985 193.812 143.417 197.74 140.643 200.803C136.156 205.757 134.563 208.355 134.018 209.924C133.503 211.41 133.877 211.72 134.002 213.644C134.061 214.553 134.021 215.551 133.525 216.476C133.002 217.451 132.134 218.054 131.233 218.496C130.378 218.915 129.277 219.29 128.056 219.697C126.784 220.12 125.242 220.619 123.311 221.317C120.75 222.243 117.881 224.721 115.231 227.774C112.644 230.755 110.516 233.988 109.366 236.026C108.784 237.056 107.337 237.427 106.133 236.855C104.929 236.282 104.425 234.983 105.006 233.953C106.265 231.722 108.536 228.267 111.321 225.058C114.044 221.922 117.521 218.705 121.259 217.354C123.264 216.629 124.879 216.103 126.12 215.69C127.413 215.259 128.185 214.986 128.697 214.735C128.997 214.589 129.1 214.499 129.128 214.469C129.141 214.439 129.207 214.251 129.167 213.644C129.107 212.723 128.537 210.896 129.378 208.468C130.191 206.124 132.259 203.029 136.792 198.024C138.528 196.106 139.078 193.452 138.579 190.219C138.211 187.828 137.279 185.235 135.973 182.63C131.098 180.938 127.469 181.604 124.827 183.013C121.939 184.554 120.068 187.084 119.27 188.906C118.797 189.983 117.399 190.465 116.148 189.987C114.898 189.508 114.266 188.249 114.738 187.172C115.81 184.725 118.233 181.416 122.147 179.328C125.045 177.781 128.681 176.953 132.973 177.553C131.199 174.94 129.203 172.521 127.255 170.538C124.661 167.897 122.413 166.294 121.073 165.799C119.816 165.334 119.166 164.077 119.622 162.994ZM140.819 161.89C141.274 160.807 142.664 160.305 143.921 160.769C148.834 162.584 157.532 169.965 159.87 185.337C160.216 187.606 160.576 189.086 160.944 190.045C161.258 190.862 161.505 191.107 161.559 191.154C161.608 191.169 161.768 191.202 162.159 191.195C162.471 191.19 162.831 191.165 163.322 191.126C163.79 191.089 164.357 191.043 164.97 191.018C167.57 190.913 170.931 191.199 175.162 193.508C179.285 195.758 184.106 199.859 189.912 207.105C190.703 208.093 190.472 209.417 189.395 210.06C188.318 210.702 186.801 210.422 186.009 209.434C180.414 202.452 176.077 198.901 172.786 197.105C169.601 195.367 167.289 195.208 165.468 195.281C164.978 195.301 164.518 195.34 164.029 195.379C163.565 195.416 163.037 195.456 162.527 195.465C161.507 195.482 160.14 195.383 158.861 194.559C157.622 193.761 156.883 192.553 156.364 191.201C155.838 189.832 155.431 188.02 155.072 185.665C152.908 171.439 145.067 165.726 142.272 164.694C141.015 164.229 140.363 162.973 140.819 161.89Z" fill="#2DD4BF" fillOpacity={scanStatus === "complete" ? 1 : 0.6} />
                    <g stroke="#2DD4BF" strokeWidth="0.5" strokeOpacity="0.4" strokeDasharray="2 1">
                      <path d="M147,15 L147,260 M115,92 L115,260 M165,87 L165,260 M71,112 L71,200 M202,147 L202,250" />
                      <path d="M58,168 C58,168 140,160 250,182" />
                      <path d="M60,194 C60,194 140,190 230,220" />
                    </g>
                  </svg>
                </div>

                {/* 스캔 라인 */}
                {scanStatus === "scanning" && (
                  <div className="absolute left-1/2 -translate-x-1/2 w-64 h-1.5 bg-gradient-to-r from-transparent via-[#2DD4BF] to-transparent shadow-[0_0_25px_#2DD4BF] animate-palm-scan z-20" />
                )}

                {/* 완료 오버레이 */}
                {scanStatus === "complete" && (
                  <div className="absolute inset-0 flex flex-col items-center justify-center z-30">
                    <div className="flex flex-col items-center animate-check-pop bg-white/5 backdrop-blur-xl px-12 py-8 rounded-[3rem] shadow-[0_20px_50px_rgba(0,0,0,0.3)]">
                      <div className="w-20 h-20 bg-emerald-500/20 rounded-full flex items-center justify-center mb-5 border border-emerald-500/40 shadow-[0_0_20px_rgba(16,185,129,0.2)]">
                        <CheckCircle2 className="w-10 h-10 text-emerald-500" strokeWidth={2.5} />
                      </div>
                      <span className="text-emerald-500 font-bold text-2xl tracking-tight text-center">{tx.analysisComplete}</span>
                      <p className="text-white/70 text-[11px] mt-2 font-medium">{tx.veinRecognizedAuthSuffix.trim()}</p>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </section>

      </main>
    </div>
  );
}

export default function VeinAuthCameraPage() {
  return (
    <Suspense>
      <CameraView />
    </Suspense>
  );
}
