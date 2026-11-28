import type { Env } from './api';
import { PackageError, type Quote } from './catalogue';

export type CheckoutStart = {
  provider: 'sandbox' | 'stripe';
  mode: 'sandbox';
  providerRef: string;
  redirectUrl?: string;
};

/**
 * Stripe is the recommended live provider, and this adapter only accepts
 * test keys. Card numbers are never read. Checkout is a hosted Stripe page.
 */
export async function startPayment(env: Env, orderId: string, quote: Quote, email: string, origin: string): Promise<CheckoutStart> {
  const provider = (env.PAYMENT_PROVIDER || 'sandbox').trim().toLowerCase();
  if (provider === 'sandbox' || provider === 'test') {
    return { provider: 'sandbox', mode: 'sandbox', providerRef: `sandbox_${orderId}` };
  }
  if (provider === 'stripe') return startStripe(env, orderId, quote, email, origin);
  throw new PackageError(`Unknown payment provider ${provider}`);
}

export function assertSandboxConfirm(provider: string) {
  if (provider !== 'sandbox') throw new PackageError('This order is completed by the payment provider, not by the sandbox button', 409);
}

async function startStripe(env: Env, orderId: string, quote: Quote, email: string, origin: string): Promise<CheckoutStart> {
  const key = stripeTestKey(env.STRIPE_SECRET_KEY);
  const body = new URLSearchParams();
  body.set('mode', 'payment');
  body.set('success_url', `${origin}/checkout?order=${encodeURIComponent(orderId)}&paid=1`);
  body.set('cancel_url', `${origin}/checkout?order=${encodeURIComponent(orderId)}&cancelled=1`);
  body.set('client_reference_id', orderId);
  body.set('customer_email', email);
  body.set('metadata[orderId]', orderId);
  body.set('line_items[0][quantity]', '1');
  body.set('line_items[0][price_data][currency]', quote.currency.toLowerCase());
  body.set('line_items[0][price_data][unit_amount]', String(quote.amountCents));
  body.set('line_items[0][price_data][product_data][name]', `Racing Wheel Pro ${quote.lines.map((line) => line.label).join(', ').slice(0, 120)}`);
  const response = await fetch('https://api.stripe.com/v1/checkout/sessions', {
    method: 'POST',
    headers: { authorization: `Bearer ${key}`, 'content-type': 'application/x-www-form-urlencoded' },
    body,
  });
  const payload = (await response.json().catch(() => ({}))) as { id?: string; url?: string; error?: { message?: string } };
  if (!response.ok || !payload.id) {
    throw new PackageError(payload.error?.message || 'Stripe test checkout failed', 502);
  }
  return { provider: 'stripe', mode: 'sandbox', providerRef: payload.id, redirectUrl: payload.url };
}

export function stripeTestKey(value: string | undefined): string {
  const key = (value || '').trim();
  if (!key) throw new PackageError('Stripe test key is not configured', 503);
  if (key.startsWith('sk_live_') || key.startsWith('rk_live_')) {
    throw new PackageError('Live Stripe keys are refused. Use an sk_test_ key.', 400);
  }
  if (!key.startsWith('sk_test_')) throw new PackageError('Stripe key must be a test key (sk_test_)', 400);
  return key;
}

export type PaidNotice = { orderId: string; providerRef: string; amountCents: number | null };

/** Stripe's signed webhook. The body is the event. No card fields are stored. */
export async function readStripeEvent(request: Request, secret: string | undefined): Promise<PaidNotice> {
  const key = (secret || '').trim();
  if (!key || key.startsWith('whsec_live')) throw new PackageError('Stripe webhook secret is not a test secret', 503);
  const payload = await request.text();
  const header = request.headers.get('stripe-signature') || '';
  if (!(await stripeSignatureOk(payload, header, key))) throw new PackageError('Invalid Stripe signature', 400);
  const event = JSON.parse(payload) as {
    type?: string;
    data?: { object?: { id?: string; client_reference_id?: string; payment_status?: string; amount_total?: number; metadata?: { orderId?: string } } };
  };
  if (event.type !== 'checkout.session.completed') throw new PackageError('Ignored Stripe event', 202);
  const session = event.data?.object;
  if (!session || session.payment_status !== 'paid') throw new PackageError('Stripe session is not paid', 409);
  const orderId = session.metadata?.orderId || session.client_reference_id || '';
  if (!orderId) throw new PackageError('Stripe session has no order', 400);
  return { orderId, providerRef: session.id || '', amountCents: typeof session.amount_total === 'number' ? session.amount_total : null };
}

export async function stripeSignatureOk(payload: string, header: string, secret: string): Promise<boolean> {
  const parts = new Map<string, string[]>();
  for (const piece of header.split(',')) {
    const eq = piece.indexOf('=');
    if (eq < 0) continue;
    const name = piece.slice(0, eq).trim();
    const value = piece.slice(eq + 1).trim();
    parts.set(name, [...(parts.get(name) ?? []), value]);
  }
  const timestamp = parts.get('t')?.[0];
  const signatures = parts.get('v1') ?? [];
  if (!timestamp || signatures.length === 0) return false;
  const age = Math.abs(Date.now() / 1000 - Number(timestamp));
  if (!Number.isFinite(age) || age > 300) return false;
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const mac = new Uint8Array(await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(`${timestamp}.${payload}`)));
  const expected = [...mac].map((byte) => byte.toString(16).padStart(2, '0')).join('');
  return signatures.some((signature) => timingSafeEqual(signature, expected));
}

function timingSafeEqual(left: string, right: string) {
  if (left.length !== right.length) return false;
  let diff = 0;
  for (let index = 0; index < left.length; index++) diff |= left.charCodeAt(index) ^ right.charCodeAt(index);
  return diff === 0;
}
