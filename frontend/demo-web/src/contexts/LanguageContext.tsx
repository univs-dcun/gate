"use client";
import { createContext, useContext, useState, useEffect } from "react";

export type Lang = "ko" | "en";
const LanguageContext = createContext<{ lang: Lang; toggleLang: () => void }>({ lang: "ko", toggleLang: () => {} });

export function LanguageProvider({ children }: { children: React.ReactNode }) {
  const [lang, setLang] = useState<Lang>("ko");
  useEffect(() => {
    const saved = sessionStorage.getItem("univs_lang") as Lang;
    if (saved === "ko" || saved === "en") setLang(saved);
  }, []);
  const toggleLang = () => {
    const next: Lang = lang === "ko" ? "en" : "ko";
    setLang(next);
    sessionStorage.setItem("univs_lang", next);
  };
  return <LanguageContext.Provider value={{ lang, toggleLang }}>{children}</LanguageContext.Provider>;
}
export const useLanguage = () => useContext(LanguageContext);
