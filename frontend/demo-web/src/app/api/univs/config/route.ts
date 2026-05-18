import { NextRequest, NextResponse } from "next/server";
import https from "https";

const getBaseUrl = () => process.env.UNIVS_API_BASE_URL ?? "https://develop.univs.ai:18090";

function httpsGet(url: string, headers: Record<string, string>, body: string): Promise<{ status: number; data: unknown }> {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const req = https.request(
      {
        hostname: u.hostname,
        port: u.port || 443,
        path: u.pathname + u.search,
        method: "GET",
        headers: { ...headers, "Content-Length": Buffer.byteLength(body) },
        rejectUnauthorized: false,
      },
      (res) => {
        let raw = "";
        res.on("data", (chunk) => (raw += chunk));
        res.on("end", () => {
          try { resolve({ status: res.statusCode ?? 200, data: JSON.parse(raw) }); }
          catch { resolve({ status: res.statusCode ?? 200, data: null }); }
        });
      }
    );
    req.on("error", reject);
    req.write(body);
    req.end();
  });
}

export async function GET(req: NextRequest) {
  const apiKey = req.headers.get("x-api-key") ?? "";
  try {
    const { status, data } = await httpsGet(
      `${getBaseUrl()}/api/v1/demo/config`,
      {
        "accept": "*/*",
        "Accept-Language": req.headers.get("accept-language") ?? "ko",
        "Accept-TimeZone": req.headers.get("accept-timezone") ?? "Asia/Seoul",
        "X-Api-Key": apiKey,
        "Content-Type": "application/json",
      },
      JSON.stringify({ apiKey })
    );
    return NextResponse.json(data, { status });
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    return NextResponse.json(
      { success: false, errors: { code: "PROXY_ERROR", message: msg } },
      { status: 502 }
    );
  }
}
