import { NextRequest, NextResponse } from "next/server";

const getBaseUrl = () => process.env.UNIVS_API_BASE_URL ?? "https://develop.univs.ai:18090";

process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";

export async function GET(req: NextRequest) {
  const filePath = req.nextUrl.searchParams.get("filePath");
  if (!filePath) {
    return NextResponse.json({ error: "filePath required" }, { status: 400 });
  }

  const apiKey = req.nextUrl.searchParams.get("apiKey") ?? "";
  const qs = new URLSearchParams({ filePath });
  if (apiKey) qs.set("apiKey", apiKey);

  try {
    const upstream = await fetch(`${getBaseUrl()}/api/v1/file?${qs.toString()}`, {
      headers: {
        accept: "image/*",
        ...(apiKey ? { "X-Api-Key": apiKey } : {}),
      },
    });

    if (!upstream.ok) {
      return NextResponse.json({ error: "upstream error" }, { status: upstream.status });
    }

    const contentType = upstream.headers.get("content-type") ?? "image/jpeg";
    const buffer = await upstream.arrayBuffer();
    return new NextResponse(buffer, {
      status: 200,
      headers: { "content-type": contentType },
    });
  } catch (e) {
    return NextResponse.json({ error: String(e) }, { status: 502 });
  }
}
