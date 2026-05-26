// Minimal, zero-dependency Gemini proxy for local development.
//
// It holds the Gemini API key server-side and forwards requests from the Keel app
// (KEEL_GEMINI_BASE_URL) to Google, injecting the key and streaming the SSE
// response straight back. This is the same job a Supabase Edge Function / Cloud
// Run service would do in production; the forwarding logic ports over unchanged.
//
// The key is read from the GEMINI_API_KEY env var, or from the gitignored file
// Config/.secrets/gemini.key. It is never logged and never sent to the client.
//
// Run:  GEMINI_API_KEY=xxxx node proxy/gemini-proxy.mjs
//   or: echo 'xxxx' > Config/.secrets/gemini.key && node proxy/gemini-proxy.mjs

import http from 'node:http';
import { readFileSync, existsSync } from 'node:fs';
import { Readable } from 'node:stream';

const PORT = process.env.PORT || 8787;
const UPSTREAM = 'https://generativelanguage.googleapis.com';
const KEY_FILE = new URL('../Config/.secrets/gemini.key', import.meta.url);

function apiKey() {
  if (process.env.GEMINI_API_KEY && process.env.GEMINI_API_KEY.trim()) {
    return process.env.GEMINI_API_KEY.trim();
  }
  if (existsSync(KEY_FILE)) {
    const k = readFileSync(KEY_FILE, 'utf8').trim();
    if (k) return k;
  }
  return null;
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(apiKey() ? 200 : 503, { 'content-type': 'text/plain' });
    res.end(apiKey() ? 'ok' : 'no key');
    return;
  }

  const key = apiKey();
  if (!key) {
    res.writeHead(500, { 'content-type': 'text/plain' });
    res.end('No Gemini API key. Set GEMINI_API_KEY or write Config/.secrets/gemini.key');
    return;
  }

  // Buffer the incoming body, then forward the same path + query to Google.
  const chunks = [];
  for await (const c of req) chunks.push(c);
  const body = Buffer.concat(chunks);
  const target = UPSTREAM + req.url;

  try {
    const upstream = await fetch(target, {
      method: req.method,
      headers: { 'content-type': 'application/json', 'x-goog-api-key': key },
      body: req.method === 'GET' || req.method === 'HEAD' ? undefined : body,
    });
    res.writeHead(upstream.status, {
      'content-type': upstream.headers.get('content-type') || 'application/json',
    });
    if (upstream.body) {
      Readable.fromWeb(upstream.body).pipe(res); // stream SSE straight through
    } else {
      res.end();
    }
    console.log(`${req.method} ${req.url.split('?')[0]} -> ${upstream.status}`);
  } catch (e) {
    res.writeHead(502, { 'content-type': 'text/plain' });
    res.end('proxy error: ' + e.message);
    console.error('proxy error:', e.message);
  }
});

server.listen(PORT, () => {
  console.log(`gemini proxy listening on http://localhost:${PORT} -> ${UPSTREAM}`);
  console.log(apiKey() ? 'key: loaded' : 'key: MISSING (set GEMINI_API_KEY or Config/.secrets/gemini.key)');
});
