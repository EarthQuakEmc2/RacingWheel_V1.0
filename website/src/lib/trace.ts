export type Sample = {
  tMs: number
  pos: number
  speed: number
  gas: number
  brake: number
  steer: number
  rpm: number
  gear: number
}

export function asSamples(value: unknown): Sample[] {
  if (!Array.isArray(value)) return []
  return value.map((item) => {
    if (Array.isArray(item)) {
      return {
        tMs: Number(item[0]) || 0,
        pos: Number(item[1]) || 0,
        speed: Number(item[2]) || 0,
        gas: Number(item[3]) || 0,
        brake: Number(item[4]) || 0,
        steer: Number(item[5]) || 0,
        rpm: Number(item[6]) || 0,
        gear: Number(item[7]) || 0,
      }
    }
    const row = item as Partial<Sample>
    return {
      tMs: Number(row.tMs) || 0,
      pos: Number(row.pos) || 0,
      speed: Number(row.speed) || 0,
      gas: Number(row.gas) || 0,
      brake: Number(row.brake) || 0,
      steer: Number(row.steer) || 0,
      rpm: Number(row.rpm) || 0,
      gear: Number(row.gear) || 0,
    }
  })
}

export function formatLap(ms: number | null | undefined) {
  if (ms == null || !Number.isFinite(ms)) return '—'
  const sign = ms < 0 ? '−' : ''
  const abs = Math.abs(Math.round(ms))
  const minutes = Math.floor(abs / 60000)
  const seconds = Math.floor((abs % 60000) / 1000)
  const millis = abs % 1000
  return `${sign}${minutes}:${String(seconds).padStart(2, '0')}.${String(millis).padStart(3, '0')}`
}

export function formatGap(ms: number) {
  if (!ms) return '—'
  return `${ms > 0 ? '+' : '−'}${formatLap(Math.abs(ms)).replace('−', '')}`
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && typeof init.body === 'string' && !headers.has('content-type')) {
    headers.set('content-type', 'application/json')
  }
  const response = await fetch(path, { ...init, headers, credentials: 'same-origin' })
  const body = (await response.json().catch(() => ({}))) as T & { error?: string }
  if (!response.ok) {
    const error = new Error(body.error || `Request failed (${response.status})`) as Error & { status: number; body: T }
    error.status = response.status
    error.body = body
    throw error
  }
  return body
}
