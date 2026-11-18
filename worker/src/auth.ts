/** Email + password sessions. PBKDF2 iteration count is capped so a login fits the Workers Free CPU limit. */

export const PBKDF2_ITERATIONS = 10_000;
export const FREE_DAILY_ANALYSES = 5;
export const PREMIUM_DAILY_ANALYSES = 40;
const COOKIE = 'rwp_session';

export async function hashPassword(password: string, salt?: Uint8Array) {
  const used = salt ?? crypto.getRandomValues(new Uint8Array(16));
  const bits = await pbkdf2(password, used, PBKDF2_ITERATIONS);
  return `pbkdf2$${PBKDF2_ITERATIONS}$${b64(used)}$${b64(bits)}`;
}

export async function verifyPassword(password: string, stored: string) {
  const parts = stored.split('$');
  if (parts.length !== 4 || parts[0] !== 'pbkdf2') return false;
  const iterations = Number(parts[1]);
  if (!Number.isFinite(iterations) || iterations < 1 || iterations > 200_000) return false;
  const salt = fromB64(parts[2]);
  const expected = fromB64(parts[3]);
  const actual = await pbkdf2(password, salt, iterations);
  return safeEqual(actual, expected);
}

async function pbkdf2(password: string, salt: Uint8Array, iterations: number) {
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', salt: salt as BufferSource, iterations, hash: 'SHA-256' }, key, 256);
  return new Uint8Array(bits);
}

export async function sha256Hex(value: string) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

export function randomToken(bytes = 32) {
  const raw = crypto.getRandomValues(new Uint8Array(bytes));
  return b64(raw).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');
}

export function pairingCode() {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  const raw = crypto.getRandomValues(new Uint8Array(8));
  let code = '';
  for (const byte of raw) code += alphabet[byte % alphabet.length];
  return `${code.slice(0, 4)}-${code.slice(4)}`;
}

export function normalizeCode(code: string) {
  return code.toUpperCase().replace(/[^A-Z0-9]/g, '');
}

export function sessionCookie(token: string, secure: boolean, maxAgeSeconds: number) {
  const parts = [`${COOKIE}=${token}`, 'HttpOnly', 'Path=/', 'SameSite=Lax', `Max-Age=${maxAgeSeconds}`];
  if (secure) parts.push('Secure');
  return parts.join('; ');
}

export function readCookie(header: string | null, name = COOKIE) {
  if (!header) return null;
  for (const part of header.split(';')) {
    const trimmed = part.trim();
    const eq = trimmed.indexOf('=');
    if (eq < 0) continue;
    if (trimmed.slice(0, eq) === name) return decodeURIComponent(trimmed.slice(eq + 1));
  }
  return null;
}

export function isSecureRequest(request: Request) {
  const url = new URL(request.url);
  if (url.protocol === 'https:') return true;
  return (request.headers.get('x-forwarded-proto') || '').includes('https');
}

export function validEmail(email: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) && email.length <= 120;
}

function b64(bytes: Uint8Array) {
  let text = '';
  for (const byte of bytes) text += String.fromCharCode(byte);
  return btoa(text);
}

function fromB64(value: string) {
  const binary = atob(value);
  const out = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index++) out[index] = binary.charCodeAt(index);
  return out;
}

function safeEqual(a: Uint8Array, b: Uint8Array) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let index = 0; index < a.length; index++) diff |= a[index] ^ b[index];
  return diff === 0;
}
