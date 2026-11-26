import {
  FREE_DAILY_ANALYSES,
  PREMIUM_DAILY_ANALYSES,
  hashPassword,
  isSecureRequest,
  normalizeCode,
  pairingCode,
  randomToken,
  readCookie,
  sessionCookie,
  sha256Hex,
  validEmail,
  verifyPassword,
} from './auth';
import { CARS, carLabel } from './cars';
import {
  type CornerMetric,
  type Sample,
  chooseCoachLaps,
  coachBrief,
  decimate,
  detectCorners,
} from './metrics';
import { parseUpload } from './srt';
import { TRACKS, catalogLength, trackLabel } from './tracks';

export const COACH_MODEL = '@cf/openai/gpt-oss-120b';
const MAX_BODY = 8_000_000;
const MAX_POINTS = 1000;

export interface D1Prepared {
  bind(...values: unknown[]): D1Prepared;
  first<T = Record<string, unknown>>(): Promise<T | null>;
  all<T = Record<string, unknown>>(): Promise<{ results: T[] }>;
  run(): Promise<{ success: boolean; meta?: { changes?: number } }>;
}

export interface D1Like {
  prepare(sql: string): D1Prepared;
  batch?(statements: D1Prepared[]): Promise<unknown>;
}

export interface AiBinding {
  run(model: string, input: unknown): Promise<unknown>;
}

export interface Env {
  DB: D1Like;
  ASSETS?: { fetch(request: Request): Promise<Response> };
  AI?: AiBinding;
}

type Auth = { userId: string; premium: number; deviceId: string | null };

export async function handleRequest(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  if (!url.pathname.startsWith('/api/')) {
    if (env.ASSETS) return env.ASSETS.fetch(request);
    return new Response('Not found', { status: 404 });
  }
  try {
    return await route(request, env, url);
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Server error';
    const status = message.startsWith('The ') || message.startsWith('No ') || message.startsWith('Unknown') || message.includes('samples') ? 400 : 500;
    if (status === 500) console.error(error);
    return json({ error: status === 400 ? message : 'Server error' }, status);
  }
}

async function route(request: Request, env: Env, url: URL): Promise<Response> {
  const path = url.pathname.replace(/\/+$/, '') || '/';
  const method = request.method.toUpperCase();
  const db = env.DB;

  if (method === 'GET' && path === '/api/health') return json({ ok: true, version: '1.0.01' });
  if (method === 'GET' && path === '/api/tracks') return json({ tracks: TRACKS });
  if (method === 'GET' && path === '/api/cars') return json({ cars: CARS });

  if (method === 'POST' && path === '/api/auth/register') return register(request, env);
  if (method === 'POST' && path === '/api/auth/login') return login(request, env);
  if (method === 'POST' && path === '/api/auth/logout') return logout(request, env);
  if (method === 'GET' && path === '/api/auth/me') {
    const auth = await requireUser(request, env);
    if (auth instanceof Response) return auth;
    const user = await db.prepare('SELECT id, email, premium, created_at FROM users WHERE id = ?').bind(auth.userId).first<{ id: string; email: string; premium: number; created_at: string }>();
    const quota = await quotaState(db, auth.userId, auth.premium);
    return json({ ...user, quota });
  }

  if (method === 'POST' && path === '/api/pair/start') return pairStart(request, db);
  if (method === 'POST' && path === '/api/pair/poll') return pairPoll(request, db);
  if (method === 'POST' && path === '/api/pair/claim') return pairClaim(request, env);

  if (method === 'GET' && path === '/api/devices') {
    const auth = await requireUser(request, env);
    if (auth instanceof Response) return auth;
    const rows = await db.prepare('SELECT id, name, created_at, last_seen_at FROM devices WHERE user_id = ? ORDER BY created_at DESC').bind(auth.userId).all();
    return json({ devices: rows.results });
  }
  const deviceMatch = path.match(/^\/api\/devices\/([^/]+)$/);
  if (deviceMatch && method === 'DELETE') {
    const auth = await requireUser(request, env);
    if (auth instanceof Response) return auth;
    await db.prepare('DELETE FROM devices WHERE id = ? AND user_id = ?').bind(deviceMatch[1], auth.userId).run();
    return json({ ok: true });
  }

  if (path === '/api/sessions' && method === 'GET') return listSessions(request, env);
  if (path === '/api/sessions' && method === 'POST') return createSession(request, env);
  if (path === '/api/upload' && method === 'POST') return createSession(request, env);

  const coachMatch = path.match(/^\/api\/sessions\/([^/]+)\/coach$/);
  if (coachMatch && (method === 'GET' || method === 'POST')) return coach(request, env, coachMatch[1], method === 'POST');

  const sessionMatch = path.match(/^\/api\/sessions\/([^/]+)$/);
  if (sessionMatch && method === 'GET') return getSession(request, env, sessionMatch[1]);
  if (sessionMatch && method === 'DELETE') return deleteSession(request, env, sessionMatch[1]);

  if (method === 'GET' && path === '/api/leaderboard') return leaderboard(url, db);

  return json({ error: 'Not found' }, 404);
}

async function register(request: Request, env: Env) {
  const body = await readJson(request);
  const email = String(body.email || '').trim().toLowerCase();
  const password = String(body.password || '');
  if (!validEmail(email)) return json({ error: 'Enter a valid email' }, 400);
  if (password.length < 8 || password.length > 200) return json({ error: 'Password must be at least 8 characters' }, 400);
  const existing = await env.DB.prepare('SELECT id FROM users WHERE email = ?').bind(email).first();
  if (existing) return json({ error: 'An account with that email already exists' }, 409);
  const id = crypto.randomUUID();
  const now = new Date().toISOString();
  await env.DB.prepare('INSERT INTO users (id, email, password_hash, premium, created_at) VALUES (?, ?, ?, 0, ?)').bind(id, email, await hashPassword(password), now).run();
  return issueSession(request, env, id, 201);
}

async function login(request: Request, env: Env) {
  const body = await readJson(request);
  const email = String(body.email || '').trim().toLowerCase();
  const password = String(body.password || '');
  const user = await env.DB.prepare('SELECT id, password_hash FROM users WHERE email = ?').bind(email).first<{ id: string; password_hash: string }>();
  if (!user || !(await verifyPassword(password, user.password_hash))) return json({ error: 'Email or password is wrong' }, 401);
  return issueSession(request, env, user.id, 200);
}

async function issueSession(request: Request, env: Env, userId: string, status: number) {
  const token = randomToken();
  const now = new Date();
  const expires = new Date(now.getTime() + 30 * 24 * 3600 * 1000);
  await env.DB.prepare('INSERT INTO auth_sessions (id, user_id, token_hash, expires_at, created_at) VALUES (?, ?, ?, ?, ?)').bind(crypto.randomUUID(), userId, await sha256Hex(token), expires.toISOString(), now.toISOString()).run();
  const headers = new Headers({ 'content-type': 'application/json; charset=utf-8', 'set-cookie': sessionCookie(token, isSecureRequest(request), 30 * 24 * 3600) });
  return new Response(JSON.stringify({ ok: true, userId }), { status, headers });
}

async function logout(request: Request, env: Env) {
  const token = readCookie(request.headers.get('cookie'));
  if (token) await env.DB.prepare('DELETE FROM auth_sessions WHERE token_hash = ?').bind(await sha256Hex(token)).run();
  const headers = new Headers({ 'content-type': 'application/json; charset=utf-8', 'set-cookie': sessionCookie('', isSecureRequest(request), 0) });
  return new Response(JSON.stringify({ ok: true }), { status: 200, headers });
}

async function pairStart(request: Request, db: D1Like) {
  const body = await readJson(request);
  const name = String(body.name || 'PC').trim().slice(0, 60) || 'PC';
  const secret = randomToken();
  const now = new Date();
  const expires = new Date(now.getTime() + 15 * 60 * 1000);
  let code = pairingCode();
  for (let attempt = 0; attempt < 5; attempt++) {
    const clash = await db.prepare('SELECT code FROM pairing_codes WHERE code = ?').bind(normalizeCode(code)).first();
    if (!clash) break;
    code = pairingCode();
  }
  await db.prepare('INSERT INTO pairing_codes (code, poll_secret_hash, device_name, created_at, expires_at) VALUES (?, ?, ?, ?, ?)').bind(normalizeCode(code), await sha256Hex(secret), name, now.toISOString(), expires.toISOString()).run();
  return json({ code, pollSecret: secret, expiresAt: expires.toISOString() });
}

async function pairPoll(request: Request, db: D1Like) {
  const body = await readJson(request);
  const secret = String(body.pollSecret || '');
  if (!secret) return json({ error: 'Missing poll secret' }, 400);
  const row = await db.prepare('SELECT code, device_name, user_id, device_id, device_token, expires_at, claimed_at FROM pairing_codes WHERE poll_secret_hash = ?').bind(await sha256Hex(secret)).first<{
    code: string;
    device_name: string;
    user_id: string | null;
    device_id: string | null;
    device_token: string | null;
    expires_at: string;
    claimed_at: string | null;
  }>();
  if (!row) return json({ error: 'Pairing code not found' }, 404);
  if (new Date(row.expires_at).getTime() < Date.now()) return json({ status: 'expired' });
  if (!row.claimed_at || !row.device_token || !row.device_id) return json({ status: 'pending', code: formatCode(row.code) });
  return json({ status: 'linked', deviceId: row.device_id, deviceToken: row.device_token, name: row.device_name });
}

async function pairClaim(request: Request, env: Env) {
  const auth = await requireUser(request, env);
  if (auth instanceof Response) return auth;
  const body = await readJson(request);
  const code = normalizeCode(String(body.code || ''));
  if (code.length < 6) return json({ error: 'Enter the code shown on the PC' }, 400);
  const row = await env.DB.prepare('SELECT code, device_name, expires_at, claimed_at FROM pairing_codes WHERE code = ?').bind(code).first<{ code: string; device_name: string; expires_at: string; claimed_at: string | null }>();
  if (!row) return json({ error: 'That code is not active' }, 404);
  if (row.claimed_at) return json({ error: 'That code was already used' }, 409);
  if (new Date(row.expires_at).getTime() < Date.now()) return json({ error: 'That code has expired' }, 410);
  const deviceId = crypto.randomUUID();
  const deviceToken = randomToken();
  const now = new Date().toISOString();
  await env.DB.prepare('INSERT INTO devices (id, user_id, name, token_hash, created_at, last_seen_at) VALUES (?, ?, ?, ?, ?, ?)').bind(deviceId, auth.userId, row.device_name, await sha256Hex(deviceToken), now, now).run();
  await env.DB.prepare('UPDATE pairing_codes SET user_id = ?, device_id = ?, device_token = ?, claimed_at = ? WHERE code = ?').bind(auth.userId, deviceId, deviceToken, now, code).run();
  return json({ device: { id: deviceId, name: row.device_name } });
}

async function listSessions(request: Request, env: Env) {
  const auth = await requireUser(request, env);
  if (auth instanceof Response) return auth;
  const rows = await env.DB.prepare('SELECT id, source, track, layout, car, track_length_m, started_at, lap_count, best_lap_ms FROM sessions WHERE user_id = ? ORDER BY started_at DESC LIMIT 100').bind(auth.userId).all();
  return json({
    sessions: rows.results.map((row) => ({
      ...(row as object),
      trackLabel: trackLabel(String((row as { track: string }).track), String((row as { layout: string }).layout || '')),
      carLabel: carLabel(String((row as { car: string }).car)),
    })),
  });
}

async function createSession(request: Request, env: Env) {
  const auth = await requireUser(request, env, true);
  if (auth instanceof Response) return auth;
  const bytes = await readBody(request);
  if (bytes.byteLength > MAX_BODY) return json({ error: 'Log is too large' }, 413);
  const text = new TextDecoder().decode(bytes);
  const parsed = parseUpload(text);
  const length = parsed.trackLengthM ?? catalogLength(parsed.track, parsed.layout);
  const id = crypto.randomUUID();
  const now = new Date().toISOString();
  const started = parsed.startedAt || now;
  let best: number | null = null;
  const lapRows: Array<{ id: string; index: number; timeMs: number | null; valid: boolean; trace: string; corners: CornerMetric[] }> = [];
  for (const lap of parsed.laps) {
    const provided = sanitizeCorners(lap.corners);
    const corners = provided ?? detectCorners(lap.samples, length);
    const trace = JSON.stringify(decimate(lap.samples, MAX_POINTS));
    const timeMs = lap.timeMs != null ? Math.round(lap.timeMs) : null;
    if (lap.valid && timeMs != null && timeMs > 0 && (best == null || timeMs < best)) best = timeMs;
    lapRows.push({ id: crypto.randomUUID(), index: lap.index, timeMs, valid: lap.valid, trace, corners });
  }
  await env.DB.prepare(
    'INSERT INTO sessions (id, user_id, device_id, source, track, layout, car, track_length_m, player, started_at, created_at, lap_count, best_lap_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
  ).bind(id, auth.userId, auth.deviceId, parsed.source, parsed.track, parsed.layout, parsed.car, length, parsed.player, started, now, lapRows.length, best).run();
  for (const lap of lapRows) {
    await env.DB.prepare('INSERT INTO laps (id, session_id, user_id, lap_index, time_ms, valid, trace_json) VALUES (?, ?, ?, ?, ?, ?, ?)').bind(lap.id, id, auth.userId, lap.index, lap.timeMs, lap.valid ? 1 : 0, lap.trace).run();
    for (const corner of lap.corners) await insertCorner(env.DB, lap.id, id, corner);
    if (lap.valid && lap.timeMs != null && lap.timeMs > 0) await considerLeaderboard(env.DB, auth.userId, parsed.track, parsed.layout, parsed.car, lap.id, id, lap.timeMs, now);
  }
  if (auth.deviceId) await env.DB.prepare('UPDATE devices SET last_seen_at = ? WHERE id = ?').bind(now, auth.deviceId).run();
  return json({ id, laps: lapRows.length, bestLapMs: best }, 201);
}

async function insertCorner(db: D1Like, lapId: string, sessionId: string, corner: CornerMetric) {
  await db.prepare(
    `INSERT INTO corner_metrics (
      id, lap_id, session_id, corner_index, name, apex_norm, brake_point_m, peak_brake, trail_brake_ms,
      min_speed_kmh, throttle_pickup_m, exit_full_throttle_pct, steering_smoothness, coasting_ms,
      steer_reversals, exit_rpm, exit_gear, metrics_json
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).bind(
    crypto.randomUUID(), lapId, sessionId, corner.index, corner.name, corner.apexNorm, corner.brakePointM, corner.peakBrake,
    corner.trailBrakeMs, corner.minSpeedKmh, corner.throttlePickupM, corner.exitFullThrottlePct, corner.steeringSmoothness,
    corner.coastingMs, corner.steerReversals, corner.exitRpm, corner.exitGear, JSON.stringify(corner),
  ).run();
}

async function considerLeaderboard(db: D1Like, userId: string, track: string, layout: string, car: string, lapId: string, sessionId: string, timeMs: number, now: string) {
  const current = await db.prepare('SELECT id, time_ms FROM leaderboard WHERE user_id = ? AND track = ? AND layout = ? AND car = ?').bind(userId, track, layout, car).first<{ id: string; time_ms: number }>();
  if (!current) {
    await db.prepare('INSERT INTO leaderboard (id, user_id, track, layout, car, lap_id, session_id, time_ms, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)').bind(crypto.randomUUID(), userId, track, layout, car, lapId, sessionId, timeMs, now).run();
    return;
  }
  if (timeMs < current.time_ms) {
    await db.prepare('UPDATE leaderboard SET lap_id = ?, session_id = ?, time_ms = ?, updated_at = ? WHERE id = ?').bind(lapId, sessionId, timeMs, now, current.id).run();
  }
}

async function getSession(request: Request, env: Env, id: string) {
  const auth = await requireUser(request, env);
  if (auth instanceof Response) return auth;
  const session = await env.DB.prepare('SELECT * FROM sessions WHERE id = ? AND user_id = ?').bind(id, auth.userId).first<Record<string, unknown>>();
  if (!session) return json({ error: 'Session not found' }, 404);
  const laps = await env.DB.prepare('SELECT id, lap_index, time_ms, valid, trace_json FROM laps WHERE session_id = ? ORDER BY lap_index').bind(id).all<{ id: string; lap_index: number; time_ms: number | null; valid: number; trace_json: string }>();
  const corners = await env.DB.prepare('SELECT lap_id, metrics_json FROM corner_metrics WHERE session_id = ? ORDER BY corner_index').bind(id).all<{ lap_id: string; metrics_json: string }>();
  const byLap = new Map<string, CornerMetric[]>();
  for (const row of corners.results) {
    const list = byLap.get(row.lap_id) ?? [];
    list.push(JSON.parse(row.metrics_json) as CornerMetric);
    byLap.set(row.lap_id, list);
  }
  const report = await env.DB.prepare('SELECT report_json, model, created_at FROM ai_reports WHERE session_id = ? ORDER BY created_at DESC LIMIT 1').bind(id).first<{ report_json: string; model: string; created_at: string }>();
  return json({
    session: {
      ...session,
      trackLabel: trackLabel(String(session.track), String(session.layout || '')),
      carLabel: carLabel(String(session.car)),
    },
    laps: laps.results.map((lap) => ({
      id: lap.id,
      index: lap.lap_index,
      timeMs: lap.time_ms,
      valid: lap.valid === 1,
      samples: JSON.parse(lap.trace_json) as Sample[],
      corners: byLap.get(lap.id) ?? [],
    })),
    coach: report ? { ...JSON.parse(report.report_json), model: report.model, createdAt: report.created_at } : null,
  });
}

async function deleteSession(request: Request, env: Env, id: string) {
  const auth = await requireUser(request, env);
  if (auth instanceof Response) return auth;
  const session = await env.DB.prepare('SELECT id FROM sessions WHERE id = ? AND user_id = ?').bind(id, auth.userId).first();
  if (!session) return json({ error: 'Session not found' }, 404);
  await env.DB.prepare('DELETE FROM leaderboard WHERE session_id = ?').bind(id).run();
  await env.DB.prepare('DELETE FROM ai_reports WHERE session_id = ?').bind(id).run();
  await env.DB.prepare('DELETE FROM corner_metrics WHERE session_id = ?').bind(id).run();
  await env.DB.prepare('DELETE FROM laps WHERE session_id = ?').bind(id).run();
  await env.DB.prepare('DELETE FROM sessions WHERE id = ?').bind(id).run();
  return json({ ok: true });
}

async function coach(request: Request, env: Env, id: string, generate: boolean) {
  const auth = await requireUser(request, env);
  if (auth instanceof Response) return auth;
  const session = await env.DB.prepare('SELECT id, track, layout, car, track_length_m FROM sessions WHERE id = ? AND user_id = ?').bind(id, auth.userId).first<{ id: string; track: string; layout: string; car: string; track_length_m: number | null }>();
  if (!session) return json({ error: 'Session not found' }, 404);
  const laps = await loadLapMetrics(env.DB, id);
  const choice = chooseCoachLaps(laps);
  if (!choice) return json({ error: 'This session has no valid lap to analyse' }, 400);
  const brief = coachBrief({
    track: trackLabel(session.track, session.layout || ''),
    layout: session.layout || '',
    car: carLabel(session.car),
    trackLengthM: session.track_length_m,
    subject: { index: choice.subject.index, timeMs: choice.subject.timeMs, corners: choice.subject.corners },
    reference: choice.reference ? { index: choice.reference.index, timeMs: choice.reference.timeMs, corners: choice.reference.corners } : null,
  });
  const metricsHash = await sha256Hex(JSON.stringify({ subject: choice.subject.index, corners: choice.subject.corners, best: choice.reference?.index ?? null }));
  const cached = await env.DB.prepare('SELECT report_json, created_at FROM ai_reports WHERE session_id = ? AND metrics_hash = ?').bind(id, metricsHash).first<{ report_json: string; created_at: string }>();
  if (cached) {
    const quota = await quotaState(env.DB, auth.userId, auth.premium);
    return json({ ...JSON.parse(cached.report_json), cached: true, createdAt: cached.created_at, quota });
  }
  if (!generate) return json({ error: 'No analysis yet', rules: brief.rules }, 404);
  const quota = await quotaState(env.DB, auth.userId, auth.premium);
  if (quota.remaining <= 0) {
    return json({ source: 'rules', quotaExceeded: true, text: brief.rules.join('\n'), rules: brief.rules, model: null, cached: false, quota }, 429);
  }
  let text = '';
  let source: 'workers-ai' | 'rules' = 'rules';
  let aiError: string | null = null;
  if (env.AI) {
    try {
      const result = await env.AI.run(COACH_MODEL, {
        messages: [
          { role: 'system', content: 'You coach sim racers. You only use the metrics you are given.' },
          { role: 'user', content: brief.prompt },
        ],
        max_tokens: 700,
      });
      text = textFromAi(result).trim();
      if (text) source = 'workers-ai';
    } catch (error) {
      aiError = error instanceof Error ? error.message : 'Workers AI failed';
    }
  } else {
    aiError = 'Workers AI binding is not configured';
  }
  if (!text) text = brief.rules.join('\n') || 'No measured difference against the reference lap.';
  const report = { source, model: source === 'workers-ai' ? COACH_MODEL : null, text, rules: brief.rules, aiError, subjectLap: choice.subject.index, referenceLap: choice.reference?.index ?? null };
  const counted = source === 'workers-ai';
  if (counted) await bumpQuota(env.DB, auth.userId);
  if (source === 'workers-ai') {
    await env.DB.prepare('INSERT INTO ai_reports (id, session_id, user_id, metrics_hash, model, report_json, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)').bind(crypto.randomUUID(), id, auth.userId, metricsHash, COACH_MODEL, JSON.stringify(report), new Date().toISOString()).run();
  }
  const after = await quotaState(env.DB, auth.userId, auth.premium);
  return json({ ...report, cached: false, quota: after });
}

async function loadLapMetrics(db: D1Like, sessionId: string) {
  const laps = await db.prepare('SELECT id, lap_index, time_ms, valid FROM laps WHERE session_id = ? ORDER BY lap_index').bind(sessionId).all<{ id: string; lap_index: number; time_ms: number | null; valid: number }>();
  const corners = await db.prepare('SELECT lap_id, metrics_json FROM corner_metrics WHERE session_id = ? ORDER BY corner_index').bind(sessionId).all<{ lap_id: string; metrics_json: string }>();
  const byLap = new Map<string, CornerMetric[]>();
  for (const row of corners.results) {
    const list = byLap.get(row.lap_id) ?? [];
    list.push(JSON.parse(row.metrics_json) as CornerMetric);
    byLap.set(row.lap_id, list);
  }
  return laps.results.map((lap) => ({
    index: lap.lap_index,
    timeMs: lap.time_ms,
    valid: lap.valid === 1,
    corners: byLap.get(lap.id) ?? [],
  }));
}

async function leaderboard(url: URL, db: D1Like) {
  const track = url.searchParams.get('track') || '';
  const layout = url.searchParams.get('layout') || '';
  const car = url.searchParams.get('car') || '';
  if (!track || !car) return json({ entries: [], track, layout, car });
  const rows = await db.prepare(
    `SELECT l.time_ms, l.session_id, l.updated_at, u.email
     FROM leaderboard l JOIN users u ON u.id = l.user_id
     WHERE l.track = ? AND l.layout = ? AND l.car = ?
     ORDER BY l.time_ms ASC LIMIT 100`,
  ).bind(track, layout, car).all<{ time_ms: number; session_id: string; updated_at: string; email: string }>();
  let best = 0;
  const entries = rows.results.map((row, index) => {
    if (index === 0) best = row.time_ms;
    return {
      position: index + 1,
      driver: row.email.split('@')[0],
      timeMs: row.time_ms,
      gapMs: row.time_ms - best,
      sessionId: row.session_id,
      updatedAt: row.updated_at,
    };
  });
  return json({ track, layout, car, trackLabel: trackLabel(track, layout), carLabel: carLabel(car), entries });
}

async function requireUser(request: Request, env: Env, allowDevice = false): Promise<Auth | Response> {
  if (!env.DB?.prepare) return json({ error: 'Sign in required' }, 401);
  const header = request.headers.get('authorization') || '';
  const bearer = header.toLowerCase().startsWith('bearer ') ? header.slice(7).trim() : '';
  if (allowDevice && bearer) {
    const device = await env.DB.prepare(
      `SELECT d.id AS device_id, d.user_id, u.premium
       FROM devices d JOIN users u ON u.id = d.user_id
       WHERE d.token_hash = ?`,
    ).bind(await sha256Hex(bearer)).first<{ device_id: string; user_id: string; premium: number }>();
    if (device) return { userId: device.user_id, premium: device.premium, deviceId: device.device_id };
  }
  const cookie = readCookie(request.headers.get('cookie'));
  if (!cookie) return json({ error: 'Sign in required' }, 401);
  const row = await env.DB.prepare(
    `SELECT s.user_id, s.expires_at, u.premium
     FROM auth_sessions s JOIN users u ON u.id = s.user_id
     WHERE s.token_hash = ?`,
  ).bind(await sha256Hex(cookie)).first<{ user_id: string; expires_at: string; premium: number }>();
  if (!row) return json({ error: 'Sign in required' }, 401);
  if (new Date(row.expires_at).getTime() < Date.now()) return json({ error: 'Session expired' }, 401);
  return { userId: row.user_id, premium: row.premium, deviceId: null };
}

async function quotaState(db: D1Like, userId: string, premium: number) {
  const day = new Date().toISOString().slice(0, 10);
  const limit = premium ? PREMIUM_DAILY_ANALYSES : FREE_DAILY_ANALYSES;
  const row = await db.prepare('SELECT used FROM ai_quota WHERE user_id = ? AND day = ?').bind(userId, day).first<{ used: number }>();
  const used = row?.used ?? 0;
  return { day, limit, used, remaining: Math.max(0, limit - used), premium: premium === 1 };
}

async function bumpQuota(db: D1Like, userId: string) {
  const day = new Date().toISOString().slice(0, 10);
  const row = await db.prepare('SELECT used FROM ai_quota WHERE user_id = ? AND day = ?').bind(userId, day).first<{ used: number }>();
  if (!row) await db.prepare('INSERT INTO ai_quota (user_id, day, used) VALUES (?, ?, 1)').bind(userId, day).run();
  else await db.prepare('UPDATE ai_quota SET used = ? WHERE user_id = ? AND day = ?').bind(row.used + 1, userId, day).run();
}

function sanitizeCorners(value: unknown): CornerMetric[] | null {
  if (!Array.isArray(value) || value.length === 0) return null;
  const corners: CornerMetric[] = [];
  for (const item of value) {
    if (!item || typeof item !== 'object') return null;
    const row = item as Partial<CornerMetric>;
    if (typeof row.peakBrake !== 'number' || typeof row.name !== 'string') return null;
    corners.push({
      index: Number(row.index) || corners.length + 1,
      name: row.name,
      startNorm: numOrNull(row.startNorm),
      endNorm: numOrNull(row.endNorm),
      apexNorm: numOrNull(row.apexNorm),
      brakePointM: numOrNull(row.brakePointM),
      brakePointNorm: numOrNull(row.brakePointNorm),
      peakBrake: row.peakBrake,
      trailBrakeMs: Number(row.trailBrakeMs) || 0,
      minSpeedKmh: numOrNull(row.minSpeedKmh),
      throttlePickupM: numOrNull(row.throttlePickupM),
      throttlePickupNorm: numOrNull(row.throttlePickupNorm),
      exitFullThrottlePct: Number(row.exitFullThrottlePct) || 0,
      steeringSmoothness: Number(row.steeringSmoothness) || 0,
      coastingMs: Number(row.coastingMs) || 0,
      steerReversals: Number(row.steerReversals) || 0,
      exitRpm: Number(row.exitRpm) || 0,
      exitGear: Number(row.exitGear) || 0,
    });
  }
  return corners;
}

function textFromAi(result: unknown): string {
  if (!result) return '';
  if (typeof result === 'string') return result;
  if (typeof result !== 'object') return '';
  const row = result as { response?: unknown; result?: unknown; choices?: Array<{ message?: { content?: string } }> };
  if (typeof row.response === 'string') return row.response;
  if (typeof row.result === 'string') return row.result;
  if (row.result && typeof row.result === 'object' && typeof (row.result as { response?: string }).response === 'string') {
    return (row.result as { response: string }).response;
  }
  const content = row.choices?.[0]?.message?.content;
  return typeof content === 'string' ? content : '';
}

async function readBody(request: Request) {
  const buf = new Uint8Array(await request.arrayBuffer());
  const encoding = (request.headers.get('content-encoding') || '').toLowerCase();
  const gzip = encoding.includes('gzip') || (buf.length > 2 && buf[0] === 0x1f && buf[1] === 0x8b);
  if (!gzip) return buf;
  const stream = new Blob([buf]).stream().pipeThrough(new DecompressionStream('gzip'));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

async function readJson(request: Request) {
  const text = new TextDecoder().decode(await readBody(request));
  if (!text.trim()) return {};
  return JSON.parse(text) as Record<string, unknown>;
}

function numOrNull(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function formatCode(code: string) {
  const clean = normalizeCode(code);
  return clean.length === 8 ? `${clean.slice(0, 4)}-${clean.slice(4)}` : clean;
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' } });
}
