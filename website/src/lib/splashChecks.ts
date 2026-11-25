/** Decisions for the website splash. Each function describes one check that already happened. */

export const SPLASH_STORAGE_KEY = 'rwp-splash-v1'
export const MIN_VISIBLE_MS = 2200

export type Step = {
  title: string
  detail: string
  ok: boolean
  stopped: boolean
}

export function secureStep(isSecureContext: boolean, protocol = 'https:'): Step {
  if (isSecureContext && protocol === 'https:') {
    return {
      title: 'Secure connection',
      detail: 'This page was loaded over HTTPS.',
      ok: true,
      stopped: false,
    }
  }
  if (isSecureContext) {
    return {
      title: 'Secure connection',
      detail: 'The browser treats this address as a secure context. It is not HTTPS.',
      ok: true,
      stopped: false,
    }
  }
  return {
    title: 'Secure connection',
    detail: 'This page is not on a secure connection.',
    ok: false,
    stopped: true,
  }
}

export function healthStep(status: number, body: { ok?: boolean; version?: string } | null): Step {
  if (status >= 200 && status < 300 && body?.ok === true) {
    const version = body.version && body.version.trim() ? body.version.trim() : 'no version field'
    return {
      title: 'Server health',
      detail: `The API health check answered ok (${version}).`,
      ok: true,
      stopped: false,
    }
  }
  if (status === 0) {
    return {
      title: 'Server health',
      detail: "Can't reach server – offline mode",
      ok: false,
      stopped: true,
    }
  }
  return {
    title: 'Server health',
    detail: 'The server answered, but the health check failed.',
    ok: false,
    stopped: true,
  }
}

/** 401 with no cookie is a completed check: nobody is signed in. A network error stops the bar. */
export function accountStep(status: number, email: string | null): Step {
  if (status === 200 && email) {
    return {
      title: 'Account',
      detail: `Signed in as ${email}.`,
      ok: true,
      stopped: false,
    }
  }
  if (status === 401) {
    return {
      title: 'Account',
      detail: 'Not signed in. The site works without an account.',
      ok: true,
      stopped: false,
    }
  }
  return {
    title: 'Account',
    detail: "Couldn't check the account session.",
    ok: false,
    stopped: true,
  }
}

export function progress(finished: number, total = 3): number {
  if (total <= 0) return 0
  return Math.min(1, Math.max(0, finished / total))
}
