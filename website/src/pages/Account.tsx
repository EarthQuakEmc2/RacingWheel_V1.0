import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import Footer from '../components/Footer'
import Header from '../components/Header'
import { api, formatLap } from '../lib/trace'

type Me = { id: string; email: string; premium: number; quota: { limit: number; used: number; remaining: number } }
type Device = { id: string; name: string; created_at: string; last_seen_at: string | null }
type SessionRow = {
  id: string
  source: string
  track: string
  layout: string
  car: string
  trackLabel: string
  carLabel: string
  started_at: string
  lap_count: number
  best_lap_ms: number | null
}

export default function AccountPage() {
  const [me, setMe] = useState<Me | null>(null)
  const [ready, setReady] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [devices, setDevices] = useState<Device[]>([])
  const [sessions, setSessions] = useState<SessionRow[]>([])
  const [packages, setPackages] = useState<string[]>([])

  async function refresh() {
    const profile = await api<Me>('/api/auth/me')
    const [deviceBody, sessionBody] = await Promise.all([
      api<{ devices: Device[] }>('/api/devices'),
      api<{ sessions: SessionRow[] }>('/api/sessions'),
    ])
    setMe(profile)
    setDevices(deviceBody.devices)
    setSessions(sessionBody.sessions)
    const owned = await api<{ packages: string[] }>('/api/entitlements').catch(() => ({ packages: [] as string[] }))
    setPackages(owned.packages ?? [])
  }

  useEffect(() => {
    refresh()
      .catch(() => setMe(null))
      .finally(() => setReady(true))
  }, [])

  return (
    <>
      <Header />
      <main className="coach-page">
        <div className="wrap">
          <p className="kicker">
            <span>00</span> Account
          </p>
          <h1>
            Your <span>rig</span>
          </h1>
          {!ready ? <p className="lede">Checking the session…</p> : null}
          {ready && !me ? <AuthForms onDone={() => refresh().then(() => setNotice('Signed in'))} /> : null}
          {me ? (
            <>
              <p className="lede">
                {me.email}
                {me.premium ? ' · Premium' : ' · Free'} · {me.quota.remaining} AI analyses left today ({me.quota.used}/{me.quota.limit})
              </p>
              {error ? <p className="form-error">{error}</p> : null}
              {notice ? <p className="form-ok">{notice}</p> : null}
              <div className="coach-grid">
                <section className="panel">
                  <h2>Link a PC</h2>
                  <p>The receiver shows a short code. Enter it here. The PC then receives a device token and can upload sessions.</p>
                  <PairForm
                    onLinked={async (name) => {
                      setNotice(`Linked ${name}`)
                      await refresh()
                    }}
                    onError={setError}
                  />
                  <ul className="device-list">
                    {devices.length === 0 ? <li>No PCs linked yet.</li> : null}
                    {devices.map((device) => (
                      <li key={device.id}>
                        <strong>{device.name}</strong>
                        <span>{device.last_seen_at ? `Last upload ${device.last_seen_at.slice(0, 16).replace('T', ' ')}` : 'Not seen yet'}</span>
                        <button
                          className="btn btn-ghost"
                          type="button"
                          onClick={async () => {
                            await api(`/api/devices/${device.id}`, { method: 'DELETE' })
                            await refresh()
                          }}
                        >
                          Unlink
                        </button>
                      </li>
                    ))}
                  </ul>
                </section>
                <section className="panel">
                  <h2>Upload a log</h2>
                  <p>Gzipped JSON in the Racing Wheel Pro session format, or a Sim Racing Telemetry CSV (tab or comma separated).</p>
                  <UploadForm
                    onUploaded={async () => {
                      setNotice('Log stored')
                      await refresh()
                    }}
                    onError={setError}
                  />
                  <button
                    className="btn btn-ghost"
                    type="button"
                    onClick={async () => {
                      await api('/api/auth/logout', { method: 'POST' })
                      setMe(null)
                      setDevices([])
                      setSessions([])
                    }}
                  >
                    Sign out
                  </button>
                </section>
              </div>
              <section className="panel">
                <h2>Steering Packages</h2>
                <p>
                  {packages.length === 0 ? 'No packages on this account yet.' : `Owned: ${packages.join(', ')}.`} The signed
                  licence is fetched by the app. <Link to="/packages">Catalogue</Link>
                </p>
              </section>
              <section className="panel">
                <h2>Sessions</h2>
                <table className="sheet">
                  <thead>
                    <tr>
                      <th>When</th>
                      <th>Track</th>
                      <th>Car</th>
                      <th>Laps</th>
                      <th>Best</th>
                    </tr>
                  </thead>
                  <tbody>
                    {sessions.length === 0 ? (
                      <tr>
                        <td colSpan={5}>No sessions yet.</td>
                      </tr>
                    ) : null}
                    {sessions.map((session) => (
                      <tr key={session.id}>
                        <td>
                          <Link to={`/session/${session.id}`}>{session.started_at.slice(0, 16).replace('T', ' ')}</Link>
                        </td>
                        <td>{session.trackLabel}</td>
                        <td>{session.carLabel}</td>
                        <td>{session.lap_count}</td>
                        <td>{formatLap(session.best_lap_ms)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </section>
            </>
          ) : null}
        </div>
      </main>
      <Footer />
    </>
  )
}

function AuthForms({ onDone }: { onDone: () => Promise<void> }) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  return (
    <form
      className="panel auth-form"
      onSubmit={async (event) => {
        event.preventDefault()
        setBusy(true)
        setError('')
        try {
          await api(`/api/auth/${mode}`, { method: 'POST', body: JSON.stringify({ email, password }) })
          await onDone()
        } catch (err) {
          setError(err instanceof Error ? err.message : 'Could not sign in')
        } finally {
          setBusy(false)
        }
      }}
    >
      <h2>{mode === 'login' ? 'Sign in' : 'Create an account'}</h2>
      <label>
        Email
        <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" autoComplete="username" required />
      </label>
      <label>
        Password
        <input value={password} onChange={(event) => setPassword(event.target.value)} type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength={8} required />
      </label>
      {error ? <p className="form-error">{error}</p> : null}
      <div className="cta">
        <button className="btn btn-red" type="submit" disabled={busy}>
          {mode === 'login' ? 'Sign in' : 'Register'}
        </button>
        <button className="btn btn-ghost" type="button" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
          {mode === 'login' ? 'Need an account?' : 'Have an account?'}
        </button>
      </div>
    </form>
  )
}

function PairForm({ onLinked, onError }: { onLinked: (name: string) => Promise<void>; onError: (message: string) => void }) {
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  return (
    <form
      className="inline-form"
      onSubmit={async (event) => {
        event.preventDefault()
        setBusy(true)
        onError('')
        try {
          const body = await api<{ device: { name: string } }>('/api/pair/claim', { method: 'POST', body: JSON.stringify({ code }) })
          setCode('')
          await onLinked(body.device.name)
        } catch (err) {
          onError(err instanceof Error ? err.message : 'Could not link')
        } finally {
          setBusy(false)
        }
      }}
    >
      <input value={code} onChange={(event) => setCode(event.target.value.toUpperCase())} placeholder="ABCD-EFGH" aria-label="Pairing code" required />
      <button className="btn btn-red" type="submit" disabled={busy}>
        Link this PC
      </button>
    </form>
  )
}

function UploadForm({ onUploaded, onError }: { onUploaded: () => Promise<void>; onError: (message: string) => void }) {
  const [busy, setBusy] = useState(false)
  return (
    <form
      className="inline-form"
      onSubmit={async (event) => {
        event.preventDefault()
        const input = event.currentTarget.elements.namedItem('log') as HTMLInputElement
        const file = input.files?.[0]
        if (!file) return
        setBusy(true)
        onError('')
        try {
          const buf = new Uint8Array(await file.arrayBuffer())
          const gzip = buf.length > 2 && buf[0] === 0x1f && buf[1] === 0x8b
          const response = await fetch('/api/upload', {
            method: 'POST',
            credentials: 'same-origin',
            headers: gzip ? { 'content-encoding': 'gzip' } : { 'content-type': file.type || 'text/plain' },
            body: buf,
          })
          const body = (await response.json().catch(() => ({}))) as { error?: string }
          if (!response.ok) throw new Error(body.error || 'Upload failed')
          input.value = ''
          await onUploaded()
        } catch (err) {
          onError(err instanceof Error ? err.message : 'Upload failed')
        } finally {
          setBusy(false)
        }
      }}
    >
      <input name="log" type="file" accept=".json,.gz,.csv,.txt,application/gzip,application/json,text/csv" required />
      <button className="btn btn-red" type="submit" disabled={busy}>
        Upload
      </button>
    </form>
  )
}
