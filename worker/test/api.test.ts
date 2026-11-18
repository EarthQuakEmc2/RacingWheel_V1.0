import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import type { Env } from '../src/api';
import { handleRequest } from '../src/api';
import { adviceForCorner, detectCorners, segmentLaps, type AcFrame, type Sample } from '../src/metrics';
import { parseUpload } from '../src/srt';
import { openDatabase, type TestDb } from '../src/sqlite';

const root = dirname(fileURLToPath(import.meta.url));

function freshDb() {
  const db = openDatabase();
  db.exec(readFileSync(join(root, '../migrations/0001_init.sql'), 'utf8'));
  return db;
}

function env(db: TestDb, ai?: Env['AI']): Env {
  return { DB: db, AI: ai };
}

async function api(db: TestDb, path: string, init: RequestInit = {}, ai?: Env['AI']) {
  const response = await handleRequest(new Request(`http://localhost${path}`, init), env(db, ai));
  const text = await response.text();
  let body: unknown = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = text;
  }
  return { status: response.status, body: body as Record<string, any>, headers: response.headers };
}

function cookieFrom(headers: Headers) {
  const raw = headers.get('set-cookie') || '';
  const match = raw.match(/rwp_session=([^;]+)/);
  return match ? `rwp_session=${match[1]}` : '';
}

describe('corner metrics', () => {
  it('measures one braked corner', () => {
    const samples: Sample[] = [
      { tMs: 0, pos: 0.1, speed: 200, gas: 1, brake: 0, steer: 0, rpm: 7000, gear: 4 },
      { tMs: 200, pos: 0.2, speed: 200, gas: 1, brake: 0, steer: 1, rpm: 7000, gear: 4 },
      { tMs: 400, pos: 0.3, speed: 180, gas: 0, brake: 0.2, steer: 10, rpm: 6500, gear: 4 },
      { tMs: 500, pos: 0.33, speed: 140, gas: 0, brake: 0.8, steer: 25, rpm: 6000, gear: 4 },
      { tMs: 600, pos: 0.36, speed: 100, gas: 0, brake: 0.4, steer: 30, rpm: 5200, gear: 3 },
      { tMs: 700, pos: 0.39, speed: 80, gas: 0, brake: 0.1, steer: 28, rpm: 4800, gear: 3 },
      { tMs: 750, pos: 0.4, speed: 82, gas: 0, brake: 0, steer: 22, rpm: 4700, gear: 3 },
      { tMs: 800, pos: 0.42, speed: 85, gas: 0.2, brake: 0, steer: 20, rpm: 5000, gear: 3 },
      { tMs: 900, pos: 0.48, speed: 120, gas: 1, brake: 0, steer: 8, rpm: 6200, gear: 3 },
      { tMs: 1000, pos: 0.55, speed: 160, gas: 1, brake: 0, steer: 2, rpm: 7000, gear: 4 },
    ];
    const corners = detectCorners(samples, 1000);
    expect(corners).toHaveLength(1);
    const corner = corners[0];
    expect(corner.name).toBe('T1');
    expect(corner.peakBrake).toBe(0.8);
    expect(corner.minSpeedKmh).toBe(80);
    expect(corner.brakePointM).toBe(300);
    expect(corner.trailBrakeMs).toBeGreaterThan(0);
    expect(corner.throttlePickupM).toBeGreaterThan(corner.brakePointM!);
    expect(corner.coastingMs).toBeGreaterThan(0);
    expect(corner.steeringSmoothness).toBeGreaterThan(0);
    expect(corner.steeringSmoothness).toBeLessThanOrEqual(100);
  });

  it('cuts a lap when completedLaps increments and rejects a pit lap', () => {
    const frames: AcFrame[] = [];
    for (let index = 0; index < 10; index++) {
      frames.push({
        tMs: index * 1000,
        pos: index / 10,
        speed: 150,
        gas: 1,
        brake: 0,
        steer: 0,
        rpm: 6000,
        gear: 4,
        completedLaps: 0,
        isInPit: false,
        lastTimeMs: 0,
      });
    }
    frames.push({
      tMs: 10000,
      pos: 0.01,
      speed: 80,
      gas: 0.2,
      brake: 0,
      steer: 0,
      rpm: 4000,
      gear: 2,
      completedLaps: 1,
      isInPit: false,
      lastTimeMs: 100000,
    });
    const laps = segmentLaps(frames);
    expect(laps).toHaveLength(1);
    expect(laps[0].timeMs).toBe(100000);
    expect(laps[0].valid).toBe(true);
    expect(laps[0].samples).toHaveLength(10);

    frames[3].isInPit = true;
    const pit = segmentLaps(frames);
    expect(pit[0].valid).toBe(false);
  });

  it('writes the three coaching lines the product promises', () => {
    expect(
      adviceForCorner({
        name: 'T1',
        brakeLaterM: 15,
        peakBrakeDelta: 0.2,
        throttleLaterM: null,
        minSpeedDeltaKmh: null,
        coastingExtraMs: null,
        smoothnessDelta: null,
        shortShift: false,
      }),
    ).toEqual(['Brake 15 m earlier but 20% softer into T1']);
    expect(
      adviceForCorner({
        name: 'T2',
        brakeLaterM: null,
        peakBrakeDelta: null,
        throttleLaterM: 12,
        minSpeedDeltaKmh: null,
        coastingExtraMs: null,
        smoothnessDelta: null,
        shortShift: false,
      }),
    ).toEqual(['Pick up throttle earlier on the exit of T2']);
    expect(
      adviceForCorner({
        name: 'T3',
        brakeLaterM: null,
        peakBrakeDelta: null,
        throttleLaterM: null,
        minSpeedDeltaKmh: null,
        coastingExtraMs: null,
        smoothnessDelta: null,
        shortShift: true,
      }),
    ).toEqual(['Keep revs high on exit of T3']);
  });
});

describe('SRT csv', () => {
  it('reads a tab-separated telemetry table', () => {
    const csv = [
      'Game: Assetto Corsa',
      'Track: monza',
      'Vehicle: ks_ferrari_488_gt3',
      'Time\tSpeed\tThrottle\tBrake\tSteering\tGear\tRPM\tLap\tDistance',
      '0\t200\t100\t0\t0\t4\t7000\t1\t0',
      '0.1\t120\t0\t80\t20\t3\t5000\t1\t100',
      '1.2\t180\t100\t0\t2\t4\t6800\t2\t10',
    ].join('\n');
    const parsed = parseUpload(csv);
    expect(parsed.track).toBe('monza');
    expect(parsed.car).toBe('ks_ferrari_488_gt3');
    expect(parsed.laps).toHaveLength(2);
    expect(parsed.laps[0].samples[1].brake).toBeCloseTo(0.8);
    expect(parsed.laps[0].samples[1].gas).toBe(0);
    expect(parsed.laps[0].samples[0].tMs).toBe(0);
    expect(parsed.laps[0].samples[1].tMs).toBeCloseTo(100);
    expect(parsed.laps[0].samples[1].pos).toBeCloseTo(1);
  });
});

describe('worker api', () => {
  it('registers, links a PC, stores a session, and coaches within the daily quota', async () => {
    const db = freshDb();
    const registered = await api(db, '/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email: 'ada@example.com', password: 'correct-horse' }),
    });
    expect(registered.status).toBe(201);
    const cookie = cookieFrom(registered.headers);
    expect(cookie).toContain('rwp_session=');
    expect(registered.headers.get('set-cookie')).toContain('HttpOnly');

    const me = await api(db, '/api/auth/me', { headers: { cookie } });
    expect(me.body.email).toBe('ada@example.com');
    expect(me.body.quota.limit).toBe(5);
    expect(me.body.premium).toBe(0);

    const wrong = await api(db, '/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email: 'ada@example.com', password: 'nope-nope' }),
    });
    expect(wrong.status).toBe(401);

    const started = await api(db, '/api/pair/start', { method: 'POST', body: JSON.stringify({ name: 'RIG' }) });
    expect(started.body.code).toMatch(/^[A-Z0-9]{4}-[A-Z0-9]{4}$/);
    const claimed = await api(db, '/api/pair/claim', {
      method: 'POST',
      headers: { cookie },
      body: JSON.stringify({ code: started.body.code }),
    });
    expect(claimed.status).toBe(200);
    const polled = await api(db, '/api/pair/poll', { method: 'POST', body: JSON.stringify({ pollSecret: started.body.pollSecret }) });
    expect(polled.body.status).toBe('linked');
    const deviceToken = polled.body.deviceToken as string;

    const upload = await api(db, '/api/sessions', {
      method: 'POST',
      headers: { authorization: `Bearer ${deviceToken}` },
      body: JSON.stringify(sessionBody()),
    });
    expect(upload.status).toBe(201);
    const sessionId = upload.body.id as string;

    const anonymous = await api(db, `/api/sessions/${sessionId}`);
    expect(anonymous.status).toBe(401);
    const detail = await api(db, `/api/sessions/${sessionId}`, { headers: { cookie } });
    expect(detail.body.laps).toHaveLength(2);
    expect(detail.body.laps[1].corners.length).toBeGreaterThan(0);
    expect(detail.body.session.best_lap_ms).toBe(100000);

    const board = await api(db, '/api/leaderboard?track=monza&layout=&car=ks_ferrari_488_gt3');
    expect(board.body.entries).toHaveLength(1);
    expect(board.body.entries[0].driver).toBe('ada');
    expect(board.body.entries[0].timeMs).toBe(100000);

    const ai = {
      async run() {
        return { response: 'Brake 15 m earlier but 20% softer into T1\nPick up throttle earlier on the exit of T2' };
      },
    };
    const first = await api(db, `/api/sessions/${sessionId}/coach`, { method: 'POST', headers: { cookie } }, ai);
    expect(first.status).toBe(200);
    expect(first.body.source).toBe('workers-ai');
    expect(first.body.text).toContain('Brake 15 m earlier');
    expect(first.body.quota.used).toBe(1);
    expect(first.body.rules.join('\n')).toContain('T1');

    const again = await api(db, `/api/sessions/${sessionId}/coach`, { method: 'POST', headers: { cookie } }, ai);
    expect(again.body.cached).toBe(true);
    expect(again.body.quota.used).toBe(1);

    db.exec("UPDATE users SET premium = 1 WHERE email = 'ada@example.com'");
    const premium = await api(db, '/api/auth/me', { headers: { cookie } });
    expect(premium.body.quota.limit).toBe(40);

    const csv = await api(db, '/api/upload', {
      method: 'POST',
      headers: { cookie, 'content-type': 'text/plain' },
      body: 'Time\tSpeed\tThrottle\tBrake\tSteering\tGear\tRPM\tDistance\n0\t150\t80\t0\t0\t3\t6000\t0\n0.4\t90\t0\t70\t30\t2\t4000\t200\n1.0\t140\t100\t0\t0\t3\t6500\t400\n',
    });
    expect(csv.status).toBe(201);

    db.close();
  });

  it('stops calling the model after the free daily quota', async () => {
    const db = freshDb();
    const registered = await api(db, '/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email: 'max@example.com', password: 'correct-horse' }),
    });
    const cookie = cookieFrom(registered.headers);
    const user = await db.prepare('SELECT id FROM users WHERE email = ?').bind('max@example.com').first<{ id: string }>();
    const day = new Date().toISOString().slice(0, 10);
    await db.prepare('INSERT INTO ai_quota (user_id, day, used) VALUES (?, ?, 5)').bind(user!.id, day).run();
    const upload = await api(db, '/api/sessions', { method: 'POST', headers: { cookie }, body: JSON.stringify(sessionBody()) });
    let calls = 0;
    const ai = {
      async run() {
        calls += 1;
        return { response: 'should not run' };
      },
    };
    const blocked = await api(db, `/api/sessions/${upload.body.id}/coach`, { method: 'POST', headers: { cookie } }, ai);
    expect(blocked.status).toBe(429);
    expect(blocked.body.quotaExceeded).toBe(true);
    expect(blocked.body.source).toBe('rules');
    expect(calls).toBe(0);
    db.close();
  });
});

function sessionBody() {
  const straight = (pos: number, tMs: number, speed = 220): Sample => ({ tMs, pos, speed, gas: 1, brake: 0, steer: 0, rpm: 7500, gear: 5 });
  const corner = (start: number, peak: number, brakeAt: number): Sample[] => {
    const rows: Sample[] = [];
    for (let step = 0; step < 8; step++) {
      const brake = step < 5 ? peak * (step === 2 ? 1 : 0.45) : 0;
      const gas = step < 4 ? 0 : step === 4 ? 0.2 : 1;
      rows.push({
        tMs: start + step * 100,
        pos: brakeAt + step * 0.01,
        speed: 180 - (step < 5 ? step * 18 : (8 - step) * 8),
        gas,
        brake,
        steer: step < 6 ? 20 + step : 4,
        rpm: step < 6 ? 6400 : 7200,
        gear: 4,
      });
    }
    return rows;
  };
  const lap = (timeMs: number, peak: number, shift: number): Sample[] => [
    straight(0.02, 0),
    ...corner(1000, peak, 0.12 + shift),
    straight(0.3, 2000),
    ...corner(3000, 0.6, 0.46),
    straight(0.7, 4000),
    straight(0.96, timeMs - 100, 240),
  ];
  return {
    schema: 'racing-wheel-pro.session/v1',
    source: 'assetto-corsa',
    startedAt: '2026-11-18T12:00:00.000Z',
    track: 'monza',
    layout: '',
    car: 'ks_ferrari_488_gt3',
    trackLengthM: 5793,
    player: 'Ada',
    laps: [
      { index: 1, timeMs: 100000, valid: true, samples: lap(100000, 0.55, 0) },
      { index: 2, timeMs: 108000, valid: true, samples: lap(108000, 0.9, 0.004) },
    ],
  };
}
