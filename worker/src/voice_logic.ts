/**
 * Push-to-talk room rules. No audio is stored. The same slug is used by the
 * Android module and by pc/AcRaceIni.cs so one Assetto Corsa server is one room.
 */

export const VOICE_LIMITS = {
  maxPeers: 8,
  maxJoinsPerMinute: 12,
  maxFramesPerSec: 55,
  maxOpusBytes: 200,
  frameMs: 20,
  sampleRate: 16000,
};

export interface RadioPeer {
  id: string;
  name: string;
  ptt: boolean;
  windowStart: number;
  frames: number;
  joinedAt: number;
}

export function slug(raw: string): string {
  let mapped = '';
  const trimmed = raw.trim();
  for (const ch of trimmed) {
    const code = ch.charCodeAt(0);
    const keep =
      (code >= 48 && code <= 57) || (code >= 65 && code <= 90) || (code >= 97 && code <= 122);
    mapped += keep ? ch : '-';
  }
  let text = mapped.replace(/-+/g, '-').replace(/^-+|-+$/g, '');
  text = text.replace(/[a-z]/g, (ch) => String.fromCharCode(ch.charCodeAt(0) - 32));
  if (text.length > 24) text = text.slice(0, 24).replace(/-+$/g, '');
  return text;
}

export function isIpv4(raw: string): boolean {
  const parts = raw.split('.');
  if (parts.length !== 4) return false;
  return parts.every((part) => {
    if (!/^\d{1,3}$/.test(part)) return false;
    const n = Number(part);
    return n >= 0 && n <= 255;
  });
}

export function roomFor(name: string, ip: string, port: number): string {
  const fromName = slug(name);
  if (fromName.length >= 4) return fromName;
  if (isIpv4(ip) && port >= 1 && port <= 65535) {
    const fromIp = slug(`IP-${ip}-${port}`);
    if (fromIp.length >= 4) return fromIp;
  }
  return '';
}

export function normalizeName(raw: string): string {
  const kept = raw
    .trim()
    .replace(/[^A-Za-z0-9 _-]/g, ' ')
    .replace(/ +/g, ' ')
    .trim()
    .slice(0, 20)
    .trim();
  return kept;
}

export function prepareVoice(
  roomRaw: string | null,
  nameRaw: string | null,
): { ok: true; room: string; name: string } | { ok: false; status: number; error: string } {
  const room = slug(roomRaw || '');
  const name = normalizeName(nameRaw || '');
  if (room.length < 4) return { ok: false, status: 400, error: 'Room code needs 4 to 24 letters or numbers' };
  if (!name) return { ok: false, status: 400, error: 'Enter a display name' };
  return { ok: true, room, name };
}

export function voicePublicInfo() {
  return {
    ok: true,
    version: '1',
    codec: 'opus',
    sampleRate: VOICE_LIMITS.sampleRate,
    frameMs: VOICE_LIMITS.frameMs,
    channels: 1,
    maxPeers: VOICE_LIMITS.maxPeers,
    maxFramesPerSec: VOICE_LIMITS.maxFramesPerSec,
    auth: 'anonymous-room',
    storedAudio: false,
  };
}

export function inspectAudio(packet: Uint8Array): { ok: true; seq: number } | { ok: false } {
  if (packet.length < 8) return { ok: false };
  if (packet[0] !== 0xa1 || packet[1] !== 1) return { ok: false };
  const seq = packet[2] | (packet[3] << 8);
  const ms = packet[4] | (packet[5] << 8);
  const len = packet[6] | (packet[7] << 8);
  if (ms !== VOICE_LIMITS.frameMs || len <= 0 || len > VOICE_LIMITS.maxOpusBytes) return { ok: false };
  if (packet.length !== 8 + len) return { ok: false };
  return { ok: true, seq };
}

export function admit(peerCount: number, recentJoins: number): 'ok' | 'full' | 'rate' {
  if (peerCount >= VOICE_LIMITS.maxPeers) return 'full';
  if (recentJoins >= VOICE_LIMITS.maxJoinsPerMinute) return 'rate';
  return 'ok';
}

export function claimPtt(peers: RadioPeer[], id: string): { ok: true } | { ok: false; name: string } {
  const holder = peers.find((peer) => peer.ptt && peer.id !== id);
  if (holder) return { ok: false, name: holder.name };
  const self = peers.find((peer) => peer.id === id);
  if (self) self.ptt = true;
  return { ok: true };
}

export function releasePtt(peers: RadioPeer[], id: string) {
  const self = peers.find((peer) => peer.id === id);
  if (self) self.ptt = false;
}

/** One second window. False means the frame is dropped and not forwarded. */
export function noteFrame(peer: RadioPeer, now: number): boolean {
  if (now - peer.windowStart >= 1000) {
    peer.windowStart = now;
    peer.frames = 0;
  }
  if (peer.frames >= VOICE_LIMITS.maxFramesPerSec) return false;
  peer.frames += 1;
  return true;
}

export function recentJoins(stamps: number[], now: number): number[] {
  return stamps.filter((stamp) => now - stamp < 60_000);
}
