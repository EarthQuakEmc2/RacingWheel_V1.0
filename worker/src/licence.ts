import { filesForTemplates, templatesFor } from './catalogue';
import { DEV_LICENCE_PUBLIC_B64 } from './devLicenceKey';

export const LICENCE_ISSUER = 'racingwheel-pro';

export type LicencePayload = {
  alg: 'Ed25519';
  files: { name: string; sha256: string; bytes: number }[];
  iat: string;
  iss: typeof LICENCE_ISSUER;
  packages: string[];
  sub: string;
  templates: string[];
  v: 1;
};

export type LicenceEnvelope = {
  payload: string;
  signature: string;
};

type KeyEnv = {
  LICENCE_PRIVATE_KEY_PKCS8?: string;
  LICENCE_MODE?: string;
};

export function canonicalJson(value: unknown): string {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map((item) => canonicalJson(item)).join(',')}]`;
  const entries = Object.entries(value as Record<string, unknown>).sort(([left], [right]) => (left < right ? -1 : left > right ? 1 : 0));
  return `{${entries.map(([key, item]) => `${JSON.stringify(key)}:${canonicalJson(item)}`).join(',')}}`;
}

export function bytesToB64(bytes: Uint8Array): string {
  let text = '';
  for (const byte of bytes) text += String.fromCharCode(byte);
  return btoa(text);
}

export function b64ToBytes(value: string): Uint8Array {
  const binary = atob(value.replaceAll('-', '+').replaceAll('_', '/'));
  const out = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index++) out[index] = binary.charCodeAt(index);
  return out;
}

export function bytesToB64url(bytes: Uint8Array): string {
  return bytesToB64(bytes).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');
}

export function devPublicKey(): Uint8Array {
  return b64ToBytes(DEV_LICENCE_PUBLIC_B64);
}

export async function importSigningKey(pkcs8B64: string): Promise<CryptoKey> {
  return crypto.subtle.importKey('pkcs8', b64ToBytes(pkcs8B64.trim()), { name: 'Ed25519' }, true, ['sign']);
}

export async function publicKeyFromPrivate(key: CryptoKey): Promise<Uint8Array> {
  const jwk = (await crypto.subtle.exportKey('jwk', key)) as JsonWebKey;
  if (!jwk.x) throw new Error('Signing key has no public part');
  return b64ToBytes(jwk.x);
}

export async function loadSigningKey(env: KeyEnv): Promise<CryptoKey> {
  const pkcs8 = (env.LICENCE_PRIVATE_KEY_PKCS8 || '').trim();
  if (!pkcs8) throw new Error('Licence signing key is not configured');
  const mode = env.LICENCE_MODE === 'dev' ? 'dev' : 'production';
  const key = await importSigningKey(pkcs8);
  const raw = await publicKeyFromPrivate(key);
  if (mode !== 'dev' && equalBytes(raw, devPublicKey())) {
    throw new Error('Refusing the committed dev licence key outside dev mode');
  }
  return key;
}

export async function signLicence(env: KeyEnv, userId: string, packageIds: string[], issuedAt = new Date().toISOString()): Promise<LicenceEnvelope> {
  const packages = [...new Set(packageIds)].sort();
  const templates = templatesFor(packages);
  const payload: LicencePayload = {
    alg: 'Ed25519',
    files: filesForTemplates(templates),
    iat: issuedAt,
    iss: LICENCE_ISSUER,
    packages,
    sub: userId,
    templates,
    v: 1,
  };
  const canonical = canonicalJson(payload);
  const key = await loadSigningKey(env);
  const signature = new Uint8Array(await crypto.subtle.sign('Ed25519', key, new TextEncoder().encode(canonical)));
  return { payload: bytesToB64url(new TextEncoder().encode(canonical)), signature: bytesToB64url(signature) };
}

export async function verifyLicence(envelope: LicenceEnvelope, publicKey: Uint8Array): Promise<LicencePayload | null> {
  try {
    const message = b64ToBytes(envelope.payload);
    const signature = b64ToBytes(envelope.signature);
    const key = await crypto.subtle.importKey('raw', publicKey, { name: 'Ed25519' }, false, ['verify']);
    const ok = await crypto.subtle.verify('Ed25519', key, signature, message);
    if (!ok) return null;
    const parsed = JSON.parse(new TextDecoder().decode(message)) as LicencePayload;
    if (parsed.v !== 1 || parsed.alg !== 'Ed25519' || parsed.iss !== LICENCE_ISSUER) return null;
    if (canonicalJson(parsed) !== new TextDecoder().decode(message)) return null;
    return parsed;
  } catch {
    return null;
  }
}

export async function sha256HexBytes(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}

function equalBytes(left: Uint8Array, right: Uint8Array) {
  if (left.length !== right.length) return false;
  let diff = 0;
  for (let index = 0; index < left.length; index++) diff |= left[index] ^ right[index];
  return diff === 0;
}
