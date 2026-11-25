import { readCookie, sha256Hex } from './auth';
import type { Env } from './api';
import {
  PackageError,
  fileOwned,
  filesForTemplates,
  packageById,
  packManifest,
  parseLines,
  publicCatalogue,
  quoteLines,
  rejectCardData,
  templatesFor,
} from './catalogue';
import { sha256HexBytes, signLicence, type LicenceEnvelope } from './licence';
import { assertSandboxConfirm, readStripeEvent, startPayment } from './payments';

type OrderRow = {
  id: string;
  user_id: string;
  provider: string;
  provider_ref: string | null;
  status: string;
  currency: string;
  amount_cents: number;
  items_json: string;
  created_at: string;
  paid_at: string | null;
};

export async function handlePackageRequest(request: Request, env: Env, path: string, method: string): Promise<Response | null> {
  try {
    if (method === 'GET' && path === '/api/packages') return json(publicCatalogue());
    if (method === 'POST' && path === '/api/packages/quote') return await quote(request);
    const one = path.match(/^\/api\/packages\/([a-z0-9-]{1,64})$/);
    if (one && method === 'GET') return packageDetail(one[1]);
    if (method === 'GET' && path === '/api/entitlements') return await entitlements(request, env);
    if (method === 'POST' && path === '/api/checkout') return await checkout(request, env);
    const confirm = path.match(/^\/api\/checkout\/([^/]+)\/confirm$/);
    if (confirm && method === 'POST') return await confirmSandbox(request, env, confirm[1]);
    if (method === 'POST' && path === '/api/payments/webhook') return await webhook(request, env);
    const packFile = path.match(/^\/api\/packs\/([a-z0-9-]{1,64})\/files\/([^/]+)$/);
    if (packFile && method === 'GET') return await sendPackFile(request, env, packFile[1], decodeURIComponent(packFile[2]));
    const pack = path.match(/^\/api\/packs\/([a-z0-9-]{1,64})$/);
    if (pack && method === 'GET') return await packListing(request, env, pack[1]);
    return null;
  } catch (error) {
    if (error instanceof PackageError) return json({ error: error.message }, error.status);
    throw error;
  }
}

async function quote(request: Request) {
  const body = await readJson(request);
  rejectCards(body);
  return json(quoteLines(parseLines(body)));
}

function packageDetail(id: string) {
  const catalogue = publicCatalogue();
  const found = [...catalogue.manufacturers, ...catalogue.classes].find((pack) => pack.id === id);
  if (!found) return json({ error: 'Unknown package' }, 404);
  return json({ package: found });
}

async function entitlements(request: Request, env: Env) {
  const userId = await userIdFrom(request, env);
  const owned = await ownedPackages(env, userId);
  const licence = await issue(env, userId, owned);
  return json({ packages: owned, templates: templatesFor(owned), licence });
}

async function checkout(request: Request, env: Env) {
  const userId = await userIdFrom(request, env);
  const body = await readJson(request);
  rejectCards(body);
  const quoted = quoteLines(parseLines(body));
  const email = await userEmail(env, userId);
  const orderId = crypto.randomUUID();
  const origin = publicOrigin(request, env);
  const started = await startPayment(env, orderId, quoted, email, origin);
  const now = new Date().toISOString();
  await env.DB.prepare(
    `INSERT INTO orders (id, user_id, provider, provider_ref, status, currency, amount_cents, items_json, created_at)
     VALUES (?, ?, ?, ?, 'pending', ?, ?, ?, ?)`,
  ).bind(orderId, userId, started.provider, started.providerRef, quoted.currency, quoted.amountCents, JSON.stringify(quoted), now).run();
  return json({
    orderId,
    status: 'pending',
    provider: started.provider,
    mode: started.mode,
    amountCents: quoted.amountCents,
    currency: quoted.currency,
    grants: quoted.grants,
    lines: quoted.lines,
    redirectUrl: started.redirectUrl ?? null,
  }, 201);
}

async function confirmSandbox(request: Request, env: Env, orderId: string) {
  const userId = await userIdFrom(request, env);
  const body = await readJson(request);
  rejectCards(body);
  const order = await loadOrder(env, orderId);
  if (!order || order.user_id !== userId) return json({ error: 'Order not found' }, 404);
  if (order.status === 'paid') return json(await paidBody(env, userId, order));
  assertSandboxConfirm(order.provider);
  await markPaid(env, order, order.provider_ref);
  const fresh = await loadOrder(env, orderId);
  return json(await paidBody(env, userId, fresh ?? order));
}

async function webhook(request: Request, env: Env) {
  const provider = (env.PAYMENT_PROVIDER || 'sandbox').toLowerCase();
  if (provider === 'sandbox' || provider === 'test') {
    return json({ error: 'Sandbox checkout uses /api/checkout/:id/confirm' }, 404);
  }
  const notice = await readStripeEvent(request, env.STRIPE_WEBHOOK_SECRET);
  if (!notice) return json({ ok: true });
  const order = await loadOrder(env, notice.orderId);
  if (!order) return json({ error: 'Order not found' }, 404);
  if (order.provider !== 'stripe') return json({ error: 'Order provider mismatch' }, 409);
  if (notice.amountCents != null && notice.amountCents !== order.amount_cents) {
    return json({ error: 'Paid amount does not match the order' }, 409);
  }
  if (order.status !== 'paid') await markPaid(env, order, notice.providerRef || order.provider_ref);
  return json({ ok: true });
}

async function packListing(request: Request, env: Env, packageId: string) {
  const userId = await userIdFrom(request, env);
  const owned = await ownedPackages(env, userId);
  if (!owned.includes(packageId)) return json({ error: 'Package is not on this account' }, 403);
  const pack = packageById(packageId);
  if (!pack) return json({ error: 'Unknown package' }, 404);
  return json({
    packageId,
    templates: pack.templates,
    files: filesForTemplates(pack.templates),
  });
}

async function sendPackFile(request: Request, env: Env, packageId: string, name: string) {
  if (!/^[a-z0-9_.-]{1,80}$/.test(name)) return json({ error: 'Unknown pack file' }, 404);
  const userId = await userIdFrom(request, env);
  const owned = await ownedPackages(env, userId);
  if (!owned.includes(packageId)) return json({ error: 'Package is not on this account' }, 403);
  const pack = packageById(packageId);
  if (!pack) return json({ error: 'Unknown package' }, 404);
  const allowed = new Set(filesForTemplates(pack.templates).map((file) => file.name));
  if (!allowed.has(name) || !fileOwned([packageId], name)) return json({ error: 'Unknown pack file' }, 404);
  const expected = packManifest.files[name];
  const bytes = await readPackBytes(env, name);
  if (!bytes) return json({ error: 'Pack file is not published yet' }, 404);
  const actual = await sha256HexBytes(bytes);
  if (actual !== expected.sha256) return json({ error: 'Pack file hash does not match the signed catalogue' }, 409);
  return new Response(bytes, {
    status: 200,
    headers: {
      'content-type': name.endsWith('.json') ? 'application/json; charset=utf-8' : 'image/png',
      'cache-control': 'private, no-store',
      'etag': `"${expected.sha256}"`,
    },
  });
}

async function readPackBytes(env: Env, name: string): Promise<Uint8Array | null> {
  const injected = env.PACK_FILES?.[name];
  if (injected) return injected;
  if (!env.ASSETS) return null;
  for (const path of [`/packs/files/${name}`, `/wheels/${name}`]) {
    const response = await env.ASSETS.fetch(new Request(`https://assets.local${path}`));
    if (response.ok) return new Uint8Array(await response.arrayBuffer());
  }
  return null;
}

async function markPaid(env: Env, order: OrderRow, providerRef: string | null) {
  const quoted = JSON.parse(order.items_json) as { grants?: string[] };
  const grants = Array.isArray(quoted.grants) ? quoted.grants : [];
  const now = new Date().toISOString();
  await env.DB.prepare(`UPDATE orders SET status = 'paid', paid_at = ?, provider_ref = ? WHERE id = ? AND status = 'pending'`)
    .bind(now, providerRef, order.id)
    .run();
  for (const packageId of grants) {
    await env.DB.prepare(
      `INSERT INTO entitlements (id, user_id, package_id, source_order_id, created_at)
       VALUES (?, ?, ?, ?, ?)
       ON CONFLICT(user_id, package_id) DO NOTHING`,
    ).bind(crypto.randomUUID(), order.user_id, packageId, order.id, now).run();
  }
}

async function paidBody(env: Env, userId: string, order: OrderRow) {
  const owned = await ownedPackages(env, userId);
  const licence = await issue(env, userId, owned);
  return {
    orderId: order.id,
    status: 'paid',
    provider: order.provider,
    mode: 'sandbox',
    amountCents: order.amount_cents,
    currency: order.currency,
    packages: owned,
    licence,
  };
}

async function issue(env: Env, userId: string, packageIds: string[]): Promise<LicenceEnvelope> {
  try {
    return await signLicence(env, userId, packageIds);
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Could not sign licence';
    throw new PackageError(message, 503);
  }
}

async function ownedPackages(env: Env, userId: string) {
  const rows = await env.DB.prepare('SELECT package_id FROM entitlements WHERE user_id = ? ORDER BY package_id').bind(userId).all<{ package_id: string }>();
  return rows.results.map((row) => row.package_id);
}

async function loadOrder(env: Env, orderId: string) {
  return env.DB.prepare(
    'SELECT id, user_id, provider, provider_ref, status, currency, amount_cents, items_json, created_at, paid_at FROM orders WHERE id = ?',
  ).bind(orderId).first<OrderRow>();
}

async function userEmail(env: Env, userId: string) {
  const row = await env.DB.prepare('SELECT email FROM users WHERE id = ?').bind(userId).first<{ email: string }>();
  if (!row) throw new PackageError('Sign in required', 401);
  return row.email;
}

async function userIdFrom(request: Request, env: Env) {
  const header = request.headers.get('authorization') || '';
  const bearer = header.toLowerCase().startsWith('bearer ') ? header.slice(7).trim() : '';
  if (bearer) {
    const device = await env.DB.prepare('SELECT user_id FROM devices WHERE token_hash = ?').bind(await sha256Hex(bearer)).first<{ user_id: string }>();
    if (device) return device.user_id;
  }
  const cookie = readCookie(request.headers.get('cookie'));
  if (!cookie) throw new PackageError('Sign in required', 401);
  const row = await env.DB.prepare(
    'SELECT user_id, expires_at FROM auth_sessions WHERE token_hash = ?',
  ).bind(await sha256Hex(cookie)).first<{ user_id: string; expires_at: string }>();
  if (!row || new Date(row.expires_at).getTime() < Date.now()) throw new PackageError('Sign in required', 401);
  return row.user_id;
}

function publicOrigin(request: Request, env: Env) {
  if (env.PUBLIC_ORIGIN) return env.PUBLIC_ORIGIN.replace(/\/$/, '');
  const url = new URL(request.url);
  return `${url.protocol}//${url.host}`;
}

function rejectCards(body: unknown) {
  rejectCardData(body);
}

async function readJson(request: Request) {
  const text = await request.text();
  if (!text.trim()) return {};
  try {
    return JSON.parse(text) as unknown;
  } catch {
    throw new PackageError('Invalid JSON');
  }
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
  });
}
