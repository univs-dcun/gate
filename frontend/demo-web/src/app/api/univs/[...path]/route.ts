import { NextRequest, NextResponse } from "next/server";

const getBaseUrl = () => process.env.UNIVS_API_BASE_URL ?? "https://develop.univs.ai:18090";

// Demo server uses a self-signed cert
process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";

async function proxyDemo(req: NextRequest, pathSegments: string[]) {
  const upstreamUrl = `${getBaseUrl()}/api/v1/demo/${pathSegments.join("/")}${req.nextUrl.search}`;

  try {
    const ct = req.headers.get("content-type") ?? "";
    const fetchOptions: RequestInit & { duplex?: string } = {
      method: req.method,
      headers: { accept: "*/*", ...(ct ? { "content-type": ct } : {}) },
    };

    if (req.body) {
      fetchOptions.body = req.body;
      fetchOptions.duplex = "half";
    }

    const upstream = await fetch(upstreamUrl, fetchOptions as RequestInit);
    const data = await upstream.json().catch(() => null);
    return NextResponse.json(data, { status: upstream.status });
  } catch (e) {
    return NextResponse.json(
      { success: false, errors: { code: "PROXY_ERROR", message: String(e) } },
      { status: 502 }
    );
  }
}

export async function POST(
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> }
) {
  return proxyDemo(req, (await params).path);
}

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> }
) {
  return proxyDemo(req, (await params).path);
}
