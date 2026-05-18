"use client";
import { LanguageProvider } from "@/contexts/LanguageContext";
import { ApiKeyProvider } from "@/contexts/ApiKeyContext";
export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <LanguageProvider>
      <ApiKeyProvider>{children}</ApiKeyProvider>
    </LanguageProvider>
  );
}
