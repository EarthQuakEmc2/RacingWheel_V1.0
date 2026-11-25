import { describe, expect, it } from 'vitest';

import { assetContentType, withAssetContentType } from '../src/assetType';

describe('asset content types', () => {
  it('maps the homepage and the built files', () => {
    expect(assetContentType('/')).toBe('text/html; charset=utf-8');
    expect(assetContentType('/privacy/')).toBe('text/html; charset=utf-8');
    expect(assetContentType('/assets/index-abc.js')).toBe('application/javascript; charset=utf-8');
    expect(assetContentType('/assets/index-abc.css')).toBe('text/css; charset=utf-8');
    expect(assetContentType('/wheels/plate.png')).toBe('image/png');
    expect(assetContentType('/favicon.svg')).toBe('image/svg+xml');
    expect(assetContentType('/font.woff2')).toBe('font/woff2');
    expect(assetContentType('/file.bin')).toBeNull();
  });

  it('replaces only a missing or generic type', async () => {
    const html = new Response('<p>hi</p>', { headers: { 'content-type': 'application/octet-stream' } });
    const fixed = withAssetContentType(html, '/');
    expect(fixed.headers.get('content-type')).toBe('text/html; charset=utf-8');
    expect(fixed.headers.get('x-content-type-options')).toBe('nosniff');

    const already = new Response('body', { headers: { 'content-type': 'text/css; charset=utf-8' } });
    expect(withAssetContentType(already, '/assets/app.css')).toBe(already);

    const redirect = new Response(null, { status: 302, headers: { location: '/account' } });
    expect(withAssetContentType(redirect, '/account')).toBe(redirect);
  });
});
