/**
 * Static files are served by the assets binding. Wrangler sets the content type
 * on upload. This rewrites a missing or generic type from the URL extension so a
 * hand upload cannot make the browser download the homepage.
 */

const TYPES: Record<string, string> = {
  html: 'text/html; charset=utf-8',
  htm: 'text/html; charset=utf-8',
  js: 'application/javascript; charset=utf-8',
  mjs: 'application/javascript; charset=utf-8',
  css: 'text/css; charset=utf-8',
  json: 'application/json; charset=utf-8',
  svg: 'image/svg+xml',
  png: 'image/png',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  gif: 'image/gif',
  webp: 'image/webp',
  ico: 'image/x-icon',
  avif: 'image/avif',
  woff: 'font/woff',
  woff2: 'font/woff2',
  ttf: 'font/ttf',
  otf: 'font/otf',
  txt: 'text/plain; charset=utf-8',
  xml: 'application/xml',
  webmanifest: 'application/manifest+json',
  mp4: 'video/mp4',
  webm: 'video/webm',
  pdf: 'application/pdf',
  wasm: 'application/wasm',
};

/** Extensionless paths are the SPA shell. An unknown extension returns null. */
export function assetContentType(pathname: string): string | null {
  const last = pathname.split('/').pop() || '';
  const dot = last.lastIndexOf('.');
  if (dot < 0) return TYPES.html;
  return TYPES[last.slice(dot + 1).toLowerCase()] ?? null;
}

export function withAssetContentType(response: Response, pathname: string): Response {
  if (response.status >= 300 && response.status < 400 && response.status !== 304) return response;
  const current = (response.headers.get('content-type') || '').toLowerCase();
  if (current && !current.startsWith('application/octet-stream')) return response;
  const type = assetContentType(pathname);
  if (!type) return response;
  const out = new Response(response.body, response);
  out.headers.set('content-type', type);
  out.headers.set('x-content-type-options', 'nosniff');
  return out;
}
