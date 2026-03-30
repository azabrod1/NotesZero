import http from "node:http";
import https from "node:https";
import { URL } from "node:url";

export const fetchCompat = typeof globalThis.fetch === "function"
  ? globalThis.fetch.bind(globalThis)
  : compatFetch;

async function compatFetch(url, init = {}) {
  const parsedUrl = new URL(url);
  const transport = parsedUrl.protocol === "https:" ? https : http;
  const method = init.method ?? "GET";
  const headers = { ...(init.headers ?? {}) };
  const body = normalizeBody(init.body);

  if (body != null && headers["Content-Length"] == null && headers["content-length"] == null) {
    headers["Content-Length"] = Buffer.byteLength(body);
  }

  return new Promise((resolve, reject) => {
    const request = transport.request(parsedUrl, { method, headers }, (response) => {
      const chunks = [];
      response.on("data", (chunk) => chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk)));
      response.on("end", () => {
        const payload = Buffer.concat(chunks).toString("utf8");
        const status = response.statusCode ?? 0;
        resolve({
          ok: status >= 200 && status < 300,
          status,
          headers: response.headers,
          async text() { return payload; },
          async json() { return payload ? JSON.parse(payload) : null; }
        });
      });
    });

    request.on("error", reject);
    if (init.timeoutMs != null) {
      request.setTimeout(init.timeoutMs, () => request.destroy(new Error(`Request timed out after ${init.timeoutMs}ms`)));
    }
    if (body != null) {
      request.write(body);
    }
    request.end();
  });
}

function normalizeBody(body) {
  if (body == null || typeof body === "string" || Buffer.isBuffer(body)) {
    return body ?? null;
  }
  return JSON.stringify(body);
}
