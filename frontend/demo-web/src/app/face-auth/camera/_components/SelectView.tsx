"use client";
import { RefObject, ChangeEvent } from "react";
import { Camera, Image as ImageIcon, ChevronRight } from "lucide-react";
import { translations } from "@/lib/translations";

type Tx = typeof translations["ko"];

interface Props {
  tx: Tx;
  fileInputRef: RefObject<HTMLInputElement | null>;
  onCamera: () => void;
  onFileChange: (e: ChangeEvent<HTMLInputElement>) => void;
}

export function SelectView({ tx, fileInputRef, onCamera, onFileChange }: Props) {
  return (
    <div className="flex-1 flex flex-col bg-white animate-fade-in px-5">
      <div className="pt-10 pb-8">
        <p className="text-[#006FFF] font-bold text-[13px] mb-2 tracking-tight">{tx.refPhotoSection}</p>
        <h2 className="text-[22px] font-bold text-slate-900 leading-[1.4]">
          {tx.howToAddRef.split("\n").map((line, i) => (
            <span key={i}>{line}{i === 0 && <br />}</span>
          ))}
        </h2>
      </div>
      <div className="flex flex-col gap-4">
        <button
          onClick={onCamera}
          className="w-full bg-white border border-slate-200 px-5 py-3.5 rounded-2xl flex items-center justify-between active:bg-slate-50 transition-all"
        >
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 bg-blue-50 rounded-xl flex items-center justify-center text-blue-500">
              <Camera className="w-6 h-6" />
            </div>
            <p className="text-[16px] font-semibold text-slate-800">{tx.takePhotoNow}</p>
          </div>
          <ChevronRight className="w-5 h-5 text-slate-300" />
        </button>
        <button
          onClick={() => fileInputRef.current?.click()}
          className="w-full bg-white border border-slate-200 px-5 py-3.5 rounded-2xl flex items-center justify-between active:bg-slate-50 transition-all"
        >
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 bg-teal-50 rounded-xl flex items-center justify-center text-teal-500">
              <ImageIcon className="w-6 h-6" />
            </div>
            <p className="text-[16px] font-semibold text-slate-800">{tx.chooseGallery}</p>
          </div>
          <ChevronRight className="w-5 h-5 text-slate-300" />
        </button>
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          className="hidden"
          onChange={onFileChange}
        />
      </div>
    </div>
  );
}
