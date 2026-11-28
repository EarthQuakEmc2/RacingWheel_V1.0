import { createHmac } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import type { Env } from '../src/api';
import { handleRequest } from '../src/api';
import { catalogue, quoteLines, validateCatalogue } from '../src/catalogue';
import { canonicalJson, devPublicKey, loadSigningKey, publicKeyFromPrivate, signLicence, verifyLicence } from '../src/licence';
import { stripeSignatureOk, stripeTestKey } from '../src/payments';
import { openDatabase, type TestDb } from '../src/sqlite';

const root = dirname(fileURLToPath(import.meta.url));
const devPrivate = readFileSync(join(root, '../dev-keys/DEV_ONLY_licence_private.b64'), 'utf8').trim();

function freshDb() {
  const db = openDatabase();
  db.exec(readFileSync(join(root, '../migrations/0001_init.sql'), 'utf8'));
  db.exec(readFileSync(join(root, '../migrations/0002_steering_packages.sql'), 'utf8'));
  return db;
}

function env(db: TestDb, extra: Partial<Env> = {}): Env {
  return {
    DB: db,
    LICENCE_MODE: 'dev',
    LICENCE_PRIVATE_KEY_PKCS8: devPrivate,
    PAYMENT_PROVIDER: 'sandbox',
    ...extra,
  };
}

async function api(db: TestDb, path: string, init: RequestInit = {}, extra: Partial<Env> = {}) {
  const response = await handleRequest(new Request(`http://localhost${path}`, init), env(db, extra));
  const text = await response.text();
  let body: Record<string, any> = {};
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = { raw: text };
    }
  }
  return { status: response.status, body, headers: response.headers, text };
}

function cookieFrom(headers: Headers) {
  const raw = headers.get('set-cookie') || '';
  const match = raw.match(/rwp_session=([^;]+)/);
  return match ? `rwp_session=${match[1]}` : '';
}

async function register(db: TestDb, email = 'driver@example.com') {
  const response = await api(db, '/api/auth/register', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email, password: 'steering-pack' }),
  });
  expect(response.status).toBe(201);
  return cookieFrom(response.headers);
}

describe('steering package catalogue', () => {
  it('matches the published prices and keeps class packs unsellable', () => {
    expect(validateCatalogue()).toEqual([]);
    expect(catalogue.manufacturers.length).toBeGreaterThanOrEqual(20);
    expect(catalogue.manufacturers.map((pack) => pack.id)).toEqual(
      expect.arrayContaining(['porsche', 'ferrari', 'lamborghini', 'bmw', 'audi', 'alfa-romeo', 'mercedes', 'mclaren']),
    );
    expect(quoteLines([{ type: 'manufacturer', id: 'porsche' }]).amountCents).toBe(399);
    const pick = quoteLines([{ type: 'bundle', id: 'pick-3', manufacturers: ['audi', 'bmw', 'porsche'] }]);
    expect(pick.amountCents).toBe(999);
    expect(pick.grants).toEqual(['audi', 'bmw', 'porsche']);
    expect(quoteLines([{ type: 'bundle', id: 'pick-6', manufacturers: ['abarth', 'audi', 'bmw', 'ferrari', 'ford', 'ktm'] }]).amountCents).toBe(1499);
    expect(quoteLines([{ type: 'bundle', id: 'pick-9', manufacturers: catalogue.manufacturers.slice(0, 9).map((pack) => pack.id) }]).amountCents).toBe(2499);
    const all = quoteLines([{ type: 'bundle', id: 'everything' }]);
    expect(all.amountCents).toBe(3499);
    expect(all.grants).toEqual(catalogue.manufacturers.map((pack) => pack.id).sort());
    expect(() => quoteLines([{ type: 'class', id: 'formula-1' }])).toThrow(/TODO/);
    expect(() => quoteLines([{ type: 'class', id: 'm3' }])).toThrow(/TODO/);
    expect(() => quoteLines([{ type: 'bundle', id: 'pick-3', manufacturers: ['porsche', 'porsche', 'audi'] }])).toThrow(/once/);
  });

  it('keeps the four free wheels out of every paid template list', () => {
    const paid = new Set(catalogue.manufacturers.flatMap((pack) => pack.templates).concat(catalogue.classes.flatMap((pack) => pack.templates)));
    for (const id of ['moza_ks', 'ferrari_sf1000', 'mercedes_amg_f1', 'mclaren_ascher_dark']) {
      expect(paid.has(id)).toBe(false);
    }
    expect(catalogue.manufacturers.find((pack) => pack.id === 'ferrari')?.includedFree).toContain('ferrari_sf1000');
  });
});

describe('sandbox checkout and signed licences', () => {
  it('refuses card fields, live Stripe keys, and the dev signing key in production', async () => {
    const db = freshDb();
    const cookie = await register(db);
    const card = await api(db, '/api/checkout', {
      method: 'POST',
      headers: { 'content-type': 'application/json', cookie },
      body: JSON.stringify({ lines: [{ type: 'manufacturer', id: 'porsche' }], card: { number: '4242424242424242', cvc: '123' } }),
    });
    expect(card.status).toBe(400);
    expect(card.body.error).toMatch(/Card data/);

    const live = await api(db, '/api/checkout', {
      method: 'POST',
      headers: { 'content-type': 'application/json', cookie },
      body: JSON.stringify({ lines: [{ type: 'manufacturer', id: 'porsche' }] }),
    }, { PAYMENT_PROVIDER: 'stripe', STRIPE_SECRET_KEY: 'sk_live_not_for_this_branch' });
    expect(live.status).toBe(400);
    expect(live.body.error).toMatch(/Live Stripe/);
    expect(() => stripeTestKey('rk_live_secret')).toThrow(/Live Stripe/);

    await expect(loadSigningKey({ LICENCE_PRIVATE_KEY_PKCS8: devPrivate, LICENCE_MODE: 'production' })).rejects.toThrow(/dev licence key/);
    const key = await loadSigningKey({ LICENCE_PRIVATE_KEY_PKCS8: devPrivate, LICENCE_MODE: 'dev' });
    const raw = await publicKeyFromPrivate(key);
    expect(Buffer.from(raw).equals(Buffer.from(devPublicKey()))).toBe(true);
  });

  it('grants a pick-3 bundle and keeps a tampered licence invalid', async () => {
    const db = freshDb();
    const cookie = await register(db);
    const created = await api(db, '/api/checkout', {
      method: 'POST',
      headers: { 'content-type': 'application/json', cookie },
      body: JSON.stringify({ lines: [{ type: 'bundle', id: 'pick-3', manufacturers: ['porsche', 'ferrari', 'mclaren'] }] }),
    });
    expect(created.status).toBe(201);
    expect(created.body.amountCents).toBe(999);
    expect(created.body.provider).toBe('sandbox');
    expect(created.body.mode).toBe('sandbox');
    expect(JSON.stringify(created.body)).not.toMatch(/4242|cvc|pan/i);

    const paid = await api(db, `/api/checkout/${created.body.orderId}/confirm`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', cookie },
      body: JSON.stringify({}),
    });
    expect(paid.status).toBe(200);
    expect(paid.body.status).toBe('paid');
    expect(paid.body.packages).toEqual(['ferrari', 'mclaren', 'porsche']);
    const licence = paid.body.licence;
    const verified = await verifyLicence(licence, devPublicKey());
    expect(verified?.templates).toContain('porsche_gt3r');
    expect(verified?.templates).not.toContain('ferrari_sf1000');
    expect(verified?.sub).toBeTruthy();

    const tampered = { ...licence, payload: `${licence.payload.slice(0, -2)}aa` };
    expect(await verifyLicence(tampered, devPublicKey())).toBeNull();

    const again = await api(db, `/api/checkout/${created.body.orderId}/confirm`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', cookie },
      body: JSON.stringify({}),
    });
    expect(again.status).toBe(200);
    const rows = await db.prepare('SELECT package_id FROM entitlements').all<{ package_id: string }>();
    expect(rows.results).toHaveLength(3);
  });

  it('serves a pack file only to the account that bought it, and only if the hash matches', async () => {
    const db = freshDb();
    const cookie = await register(db);
    const stranger = await register(db, 'other@example.com');
    const created = await api(db, '/api/checkout', {
      method: 'POST',
      headers: { 'content-type': 'application/json', cookie },
      body: JSON.stringify({ lines: [{ type: 'manufacturer', id: 'porsche' }] }),
    });
    await api(db, `/api/checkout/${created.body.orderId}/confirm`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', cookie },
      body: '{}',
    });

    const denied = await api(db, '/api/packs/porsche/files/porsche_gt3r.json', { headers: { cookie: stranger } });
    expect(denied.status).toBe(403);

    const bytes = readFileSync(join(root, '../../app/src/main/assets/wheels/porsche_gt3r.json'));
    const ok = await api(db, '/api/packs/porsche/files/porsche_gt3r.json', { headers: { cookie } }, {
      PACK_FILES: { 'porsche_gt3r.json': new Uint8Array(bytes) },
    });
    expect(ok.status).toBe(200);
    expect(ok.text).toContain('"id": "porsche_gt3r"');

    const bad = await api(db, '/api/packs/porsche/files/porsche_gt3r.json', { headers: { cookie } }, {
      PACK_FILES: { 'porsche_gt3r.json': new Uint8Array([1, 2, 3]) },
    });
    expect(bad.status).toBe(409);

    const unsigned = await api(db, '/api/entitlements');
    expect(unsigned.status).toBe(401);
  });

  it('accepts a signed Stripe test webhook and ignores the dev schema for cards', async () => {
    const schema = readFileSync(join(root, '../migrations/0002_steering_packages.sql'), 'utf8').toLowerCase();
    expect(schema).not.toMatch(/\b(card_number|pan|cvc|cvv|expiry)\b/);

    const db = freshDb();
    const cookie = await register(db);
    const me = await api(db, '/api/auth/me', { headers: { cookie } });
    const orderId = 'order-stripe-1';
    const items = quoteLines([{ type: 'manufacturer', id: 'audi' }]);
    await db.prepare(
      `INSERT INTO orders (id, user_id, provider, provider_ref, status, currency, amount_cents, items_json, created_at)
       VALUES (?, ?, 'stripe', 'cs_test_1', 'pending', 'USD', ?, ?, ?)`,
    ).bind(orderId, me.body.id, items.amountCents, JSON.stringify(items), new Date().toISOString()).run();

    const payload = JSON.stringify({
      type: 'checkout.session.completed',
      data: { object: { id: 'cs_test_1', client_reference_id: orderId, payment_status: 'paid', amount_total: 399, metadata: { orderId } } },
    });
    const timestamp = Math.floor(Date.now() / 1000);
    const signature = createHmac('sha256', 'whsec_test').update(`${timestamp}.${payload}`).digest('hex');
    expect(await stripeSignatureOk(payload, `t=${timestamp},v1=${signature}`, 'whsec_test')).toBe(true);
    const response = await api(db, '/api/payments/webhook', {
      method: 'POST',
      headers: { 'stripe-signature': `t=${timestamp},v1=${signature}`, 'content-type': 'application/json' },
      body: payload,
    }, { PAYMENT_PROVIDER: 'stripe', STRIPE_SECRET_KEY: 'sk_test_placeholder', STRIPE_WEBHOOK_SECRET: 'whsec_test' });
    expect(response.status).toBe(200);
    const owned = await api(db, '/api/entitlements', { headers: { cookie } });
    expect(owned.body.packages).toEqual(['audi']);
    const signed = await signLicence({ LICENCE_MODE: 'dev', LICENCE_PRIVATE_KEY_PKCS8: devPrivate }, me.body.id, ['audi'], '2026-01-01T00:00:00.000Z');
    const parsed = await verifyLicence(signed, devPublicKey());
    expect(canonicalJson(parsed)).toBe(new TextDecoder().decode(Uint8Array.from(Buffer.from(signed.payload.replaceAll('-', '+').replaceAll('_', '/'), 'base64'))));
  });
});
