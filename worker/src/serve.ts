import { createServer } from 'node:http';
import { readFileSync, existsSync, statSync } from 'node:fs';
import { dirname, extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';

import { handleRequest, type Env } from './api';
import { openDatabase } from './sqlite';

const here = dirname(fileURLToPath(import.meta.url));
const dist = join(here, '../../website/dist');
const migration = readFileSync(join(here, '../migrations/0001_init.sql'), 'utf8');
const db = openDatabase();
db.exec(migration);

const env: Env = {
  DB: db,
  AI: {
    async run() {
      return {
        response: [
          'Brake 15 m earlier but 20% softer into T1.',
          'Pick up throttle earlier on the exit of T2.',
          'Keep revs high on exit of T3.',
        ].join('\n'),
      };
    },
  },
};

const types: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript',
  '.css': 'text/css',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
};

function straight(pos: number, tMs: number, speed = 220) {
  return { tMs, pos, speed, gas: 1, brake: 0, steer: 2, rpm: 7600, gear: 5 };
}

function corner(start: number, pos: number, peak: number, steer: number) {
  const rows = [];
  for (let step = 0; step < 10; step++) {
    rows.push({
      tMs: start + step * 120,
      pos: pos + step * 0.012,
      speed: 210 - (step < 6 ? step * 16 : (10 - step) * 8),
      gas: step < 4 ? 0 : step === 4 ? 0.25 : 1,
      brake: step < 5 ? peak * (step === 2 ? 1 : 0.5) : 0,
      steer: step < 7 ? steer : steer * 0.2,
      rpm: step < 6 ? 6100 : 7400,
      gear: step < 8 ? 4 : 5,
    });
  }
  return rows;
}

async function seed() {
  const register = await handleRequest(
    new Request('http://localhost/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email: 'ada@example.com', password: 'correct-horse' }),
    }),
    env,
  );
  const cookie = (register.headers.get('set-cookie') || '').split(';')[0];
  await handleRequest(
    new Request('http://localhost/api/sessions', {
      method: 'POST',
      headers: { cookie, 'content-type': 'application/json' },
      body: JSON.stringify({
        schema: 'racing-wheel-pro.session/v1',
        source: 'assetto-corsa',
        startedAt: '2026-11-18T18:04:00.000Z',
        track: 'monza',
        layout: '',
        car: 'ks_ferrari_488_gt3',
        trackLengthM: 5793,
        player: 'Ada',
        laps: [
          {
            index: 1,
            timeMs: 106200,
            valid: true,
            samples: [straight(0.02, 0), ...corner(800, 0.1, 0.62, -28), straight(0.32, 2500), ...corner(4000, 0.42, 0.7, 22), straight(0.62, 6200, 230), ...corner(8000, 0.72, 0.55, -18), straight(0.95, 106000, 250)],
          },
          {
            index: 2,
            timeMs: 112800,
            valid: true,
            samples: [straight(0.02, 0, 210), ...corner(900, 0.118, 0.9, -34), straight(0.34, 2800, 200), ...corner(4300, 0.45, 0.75, 26), straight(0.64, 6800, 215), ...corner(8600, 0.74, 0.6, -16), straight(0.95, 112000, 230)],
          },
        ],
      }),
    }),
    env,
  );
  const sessions = await handleRequest(new Request('http://localhost/api/sessions', { headers: { cookie } }), env);
  const body = (await sessions.json()) as { sessions: Array<{ id: string }> };
  const id = body.sessions[0].id;
  await handleRequest(new Request(`http://localhost/api/sessions/${id}/coach`, { method: 'POST', headers: { cookie }, body: '{}' }), env);
  return { cookie, id };
}

const seeded = await seed();
console.log(JSON.stringify(seeded));

const server = createServer(async (req, res) => {
  const url = new URL(req.url || '/', 'http://127.0.0.1');
  if (url.pathname.startsWith('/api/')) {
    const chunks: Buffer[] = [];
    for await (const chunk of req) chunks.push(chunk as Buffer);
    const request = new Request('http://127.0.0.1' + url.pathname + url.search, {
      method: req.method,
      headers: req.headers as HeadersInit,
      body: req.method === 'GET' || req.method === 'HEAD' ? undefined : Buffer.concat(chunks),
    });
    const response = await handleRequest(request, env);
    res.statusCode = response.status;
    response.headers.forEach((value, key) => res.setHeader(key, value));
    res.end(Buffer.from(await response.arrayBuffer()));
    return;
  }
  let path = normalize(join(dist, url.pathname));
  if (!path.startsWith(dist)) {
    res.statusCode = 403;
    res.end('Forbidden');
    return;
  }
  if (!existsSync(path) || statSync(path).isDirectory()) path = join(dist, 'index.html');
  res.setHeader('content-type', types[extname(path)] || 'application/octet-stream');
  res.end(readFileSync(path));
});

server.listen(8787, '127.0.0.1');
