import { useEffect, useState } from 'react'
import { accountStep, healthStep, MIN_VISIBLE_MS, progress, secureStep, SPLASH_STORAGE_KEY, type Step } from '../lib/splashChecks'

type Phase = {
  steps: Step[]
  running: string
  stopped: boolean
  finished: number
}

const EMPTY: Phase = { steps: [], running: 'Checking the secure connection…', stopped: false, finished: 0 }

function alreadySeen(): boolean {
  try {
    return sessionStorage.getItem(SPLASH_STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

function markSeen() {
  try {
    sessionStorage.setItem(SPLASH_STORAGE_KEY, '1')
  } catch {
    /* private mode can refuse storage; the splash simply will not be remembered */
  }
}

export default function StartupSplash() {
  const [open, setOpen] = useState(() => !alreadySeen())
  const [phase, setPhase] = useState<Phase>(EMPTY)
  const reduced = useReducedMotion()

  useEffect(() => {
    if (!open) return
    let cancelled = false
    const started = performance.now()

    async function run() {
      const secure = secureStep(window.isSecureContext, window.location.protocol)
      if (cancelled) return
      if (secure.stopped) {
        setPhase({ steps: [secure], running: '', stopped: true, finished: 1 })
        return
      }
      setPhase({ steps: [secure], running: 'Checking server health…', stopped: false, finished: 1 })

      let healthStatus = 0
      let healthBody: { ok?: boolean; version?: string } | null = null
      try {
        const response = await fetch('/api/health', { credentials: 'same-origin', cache: 'no-store' })
        healthStatus = response.status
        healthBody = (await response.json().catch(() => null)) as { ok?: boolean; version?: string } | null
      } catch {
        healthStatus = 0
      }
      if (cancelled) return
      const health = healthStep(healthStatus, healthBody)
      if (health.stopped) {
        setPhase({ steps: [secure, health], running: '', stopped: true, finished: 2 })
        return
      }
      setPhase({ steps: [secure, health], running: 'Checking the account session…', stopped: false, finished: 2 })

      let accountStatus = 0
      let email: string | null = null
      try {
        const response = await fetch('/api/auth/me', { credentials: 'same-origin', cache: 'no-store' })
        accountStatus = response.status
        if (response.ok) {
          const body = (await response.json().catch(() => null)) as { email?: string } | null
          email = body?.email?.trim() || null
        }
      } catch {
        accountStatus = 0
      }
      if (cancelled) return
      const account = accountStep(accountStatus, email)
      const steps = [secure, health, account]
      setPhase({ steps, running: '', stopped: account.stopped, finished: 3 })
      if (account.stopped) return
      const elapsed = performance.now() - started
      const hold = reduced ? 0 : Math.max(0, MIN_VISIBLE_MS - elapsed)
      if (hold > 0) await new Promise((resolve) => window.setTimeout(resolve, hold))
      if (cancelled) return
      markSeen()
      setOpen(false)
    }

    void run()
    return () => {
      cancelled = true
    }
  }, [open, reduced])

  if (!open) return null

  function close() {
    markSeen()
    setOpen(false)
  }

  return (
    <div className={reduced ? 'splash splash-still' : 'splash'} role="status" aria-live="polite">
      <div className="splash-card">
        <p className="splash-brand">Racing Wheel Pro</p>
        <p className="splash-kicker">{phase.stopped ? 'A check stopped' : 'Starting'}</p>
        <div className="splash-track" aria-hidden="true">
          <div className={phase.stopped ? 'splash-fill is-stop' : 'splash-fill'} style={{ width: `${progress(phase.finished) * 100}%` }} />
        </div>
        <ol className="splash-steps">
          {phase.steps.map((step) => (
            <li key={step.title}>
              <span className={step.ok ? 'ok' : 'bad'}>{step.title}</span>
              <p>{step.detail}</p>
            </li>
          ))}
        </ol>
        {phase.running ? <p className="splash-running">{phase.running}</p> : null}
        <button type="button" className="splash-go" onClick={close}>
          {phase.stopped ? 'Continue anyway' : 'Continue'}
        </button>
        <p className="splash-note">The page is already loaded underneath. These lines are the checks that just ran.</p>
      </div>
    </div>
  )
}

function useReducedMotion() {
  const [reduced, setReduced] = useState(false)
  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)')
    const apply = () => setReduced(media.matches)
    apply()
    media.addEventListener('change', apply)
    return () => media.removeEventListener('change', apply)
  }, [])
  return reduced
}
