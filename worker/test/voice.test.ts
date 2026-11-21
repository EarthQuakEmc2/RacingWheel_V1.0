import { describe, expect, it } from 'vitest';

import { handleRequest } from '../src/api';
import {
  admit,
  claimPtt,
  inspectAudio,
  noteFrame,
  normalizeName,
  prepareVoice,
  recentJoins,
  releasePtt,
  roomFor,
  slug,
  type RadioPeer,
} from '../src/voice_logic';

function peer(id: string, name: string, extra: Partial<RadioPeer> = {}): RadioPeer {
  return { id, name, ptt: false, windowStart: 0, frames: 0, joinedAt: 0, ...extra };
}

describe('voice rooms', () => {
  it('builds the same room code as the phone and the PC receiver', () => {
    expect(roomFor('Official Monza #1', '10.0.0.8', 9600)).toBe('OFFICIAL-MONZA-1');
    expect(roomFor('  spa   24h ', '', 0)).toBe('SPA-24H');
    expect(roomFor('', '192.168.1.10', 9600)).toBe('IP-192-168-1-10-9600');
    expect(roomFor('AC', '192.168.1.10', 9600)).toBe('IP-192-168-1-10-9600');
    expect(roomFor('', 'not-an-ip', 9600)).toBe('');
    expect(slug('This is a very long server name indeed')).toHaveLength(24);
    expect(normalizeName('  Sam! ')).toBe('Sam');
  });

  it('rejects a bad join before any socket is accepted', () => {
    expect(prepareVoice('ab', 'Alex').ok).toBe(false);
    expect(prepareVoice('MONZA', '   ').ok).toBe(false);
    const ok = prepareVoice('monza night', 'Alex 99');
    expect(ok).toEqual({ ok: true, room: 'MONZA-NIGHT', name: 'Alex 99' });
  });

  it('gives the floor to one driver and drops extra frames', () => {
    const drivers = [peer('a', 'Alex'), peer('b', 'Sam')];
    expect(claimPtt(drivers, 'a')).toEqual({ ok: true });
    expect(claimPtt(drivers, 'b')).toEqual({ ok: false, name: 'Alex' });
    expect(drivers[0].ptt).toBe(true);
    expect(drivers[1].ptt).toBe(false);
    releasePtt(drivers, 'a');
    expect(drivers[0].ptt).toBe(false);
    expect(admit(8, 0)).toBe('full');
    expect(admit(2, 12)).toBe('rate');
    expect(admit(2, 1)).toBe('ok');

    const talker = peer('a', 'Alex', { windowStart: 1000, frames: 0 });
    for (let i = 0; i < 55; i++) expect(noteFrame(talker, 1200)).toBe(true);
    expect(noteFrame(talker, 1200)).toBe(false);
    expect(noteFrame(talker, 2200)).toBe(true);
    expect(recentJoins([0, 50_000, 59_000], 60_000)).toEqual([50_000, 59_000]);
  });

  it('accepts one 20 ms opus packet and rejects anything else', () => {
    const packet = new Uint8Array(8 + 40);
    packet[0] = 0xa1;
    packet[1] = 1;
    packet[4] = 20;
    packet[6] = 40;
    expect(inspectAudio(packet)).toEqual({ ok: true, seq: 0 });
    packet[6] = 41;
    expect(inspectAudio(packet).ok).toBe(false);
    const junk = new Uint8Array(200);
    junk.fill(7);
    expect(inspectAudio(junk).ok).toBe(false);
  });

  it('describes the relay and does not open a socket without the binding', async () => {
    const db = { prepare: () => { throw new Error('voice must not touch D1'); } };
    const info = await handleRequest(new Request('http://localhost/api/voice'), { DB: db });
    expect(info.status).toBe(200);
    const body = await info.json();
    expect(body.storedAudio).toBe(false);
    expect(body.codec).toBe('opus');
    expect(body.auth).toBe('anonymous-room');

    const missing = await handleRequest(
      new Request('http://localhost/api/voice/ws?room=MONZA&name=Alex', { headers: { upgrade: 'websocket' } }),
      { DB: db },
    );
    expect(missing.status).toBe(503);
    const refused = await handleRequest(new Request('http://localhost/api/voice/ws?room=MONZA&name=Alex'), { DB: db });
    expect(refused.status).toBe(426);
  });
});
