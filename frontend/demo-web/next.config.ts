import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    return [
      {
        source: "/api/univs/:path*",
        destination: `${process.env.UNIVS_API_BASE_URL ?? "https://develop.univs.ai:18090"}/api/v1/demo/:path*`,
      },
    ];
  },
};

export default nextConfig;
