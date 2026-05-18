import type { Metadata, Viewport } from "next";
import "./globals.css";
import { Providers } from "@/components/Providers";

export const metadata: Metadata = {
  title: "유니버스 데모테스트",
  description: "Univs AI Face Recognition Demo",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" className="h-full">
      <head>
        {/* eslint-disable-next-line @next/next/no-sync-scripts */}
        <script src="/config.js" />
      </head>
      <body className="min-h-full antialiased bg-[#e9ecef]">
        <Providers>
          <div
            id="app-container"
            className="relative mx-auto min-h-screen bg-[#f7f9fb]"
            style={{
              maxWidth: "500px",
              width: "100%",
              boxSizing: "border-box",
              boxShadow:
                "0 0 0 1px rgba(0,0,0,0.04), 8px 0 32px rgba(0,0,0,0.06), -8px 0 32px rgba(0,0,0,0.06)",
            }}
          >
            {children}
          </div>
        </Providers>
      </body>
    </html>
  );
}
