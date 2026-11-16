import {
  Component,
  lazy,
  Suspense,
  useCallback,
  useEffect,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
} from 'react'
import { clamp, createBus, damp, type SteerBus } from '../lib/steer'
import { webglAvailable } from '../lib/webgl'

const WheelScene = lazy(() => import('./WheelScene'))

const RELEASE = 'https://github.com/EarthQuakEmc2/RacingWheel_V1.0/releases/tag/v0.2.0'
const APK = 'https://github.com/EarthQuakEmc2/RacingWheel_V1.0/releases/download/v0.2.0/RacingWheelPro.apk'
const EXE = 'https://github.com/EarthQuakEmc2/RacingWheel_V1.0/releases/download/v0.1.0/RacingWheelPro-PC.exe'

const TICKER = [
  '100 Hz telemetry',
  'UDP 41511',
  'Wheels by manufacturer',
  'Buttons 1–32',
  'ViGEm Xbox 360',
  'vJoy',
  'Wi-Fi discovery',
  'USB adb reverse',
  'Assetto Corsa',
  'Other sims',
]

export default function Hero() {
  const sectionRef = useRef<HTMLElement>(null)
  const stageRef = useRef<HTMLDivElement>(null)
  const steerEl = useRef<HTMLSpanElement>(null)
  const brakeEl = useRef<HTMLElement>(null)
  const throttleEl = useRef<HTMLElement>(null)
  const fallbackRef = useRef<HTMLImageElement>(null)
  const ledsRef = useRef<HTMLDivElement>(null)
  const busRef = useRef<SteerBus>(createBus())
  const apiRef = useRef<(() => void) | null>(null)
  const inView = useRef(true)
  const [glOk] = useState(webglAvailable)
  const [failed, setFailed] = useState(false)
  const [wheelReady, setWheelReady] = useState(false)
  const showGl = glOk && !failed

  const onWheelReady = useCallback(() => setWheelReady(true), [])

  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)')
    const apply = () => {
      busRef.current.reduced = media.matches
    }
    apply()
    media.addEventListener('change', apply)
    return () => media.removeEventListener('change', apply)
  }, [])

  useEffect(() => {
    const node = sectionRef.current
    if (!node) return
    const observer = new IntersectionObserver(
      ([entry]) => {
        inView.current = entry.isIntersecting
      },
      { threshold: 0.08 },
    )
    observer.observe(node)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    let lastY = window.scrollY
    const onScroll = () => {
      const y = window.scrollY
      const dy = y - lastY
      lastY = y
      const bus = busRef.current
      bus.scroll = clamp(bus.scroll + dy * 0.004, -0.75, 0.75)
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  useEffect(() => {
    let frame = 0
    let last = performance.now()
    const loop = (now: number) => {
      const dt = Math.min(0.05, (now - last) / 1000)
      last = now
      const bus = busRef.current
      if (!bus.pointer) bus.scroll = damp(bus.scroll, 0, 1.6, dt)
      if (!bus.pointer) {
        bus.throttle = damp(bus.throttle, 0, 6, dt)
        bus.brake = damp(bus.brake, 0, 6, dt)
      }
      const idle = !bus.pointer && !bus.reduced ? Math.sin(now / 1000 * 0.5) * 0.34 : 0
      const commanded = bus.pointer ? bus.target : idle
      const mixed = bus.pointer ? commanded : commanded + bus.scroll
      bus.current = damp(bus.current, clamp(mixed, -1.15, 1.15), 5, dt)

      const deg = (bus.current * 180) / Math.PI
      if (steerEl.current) {
        steerEl.current.textContent = Math.abs(deg) < 0.8 ? '0°' : `${deg > 0 ? 'R' : 'L'} ${Math.abs(deg).toFixed(0)}°`
      }
      if (brakeEl.current) brakeEl.current.style.transform = `scaleX(${bus.brake.toFixed(3)})`
      if (throttleEl.current) throttleEl.current.style.transform = `scaleX(${bus.throttle.toFixed(3)})`
      if (fallbackRef.current) fallbackRef.current.style.transform = `rotate(${deg.toFixed(2)}deg)`
      const stage = stageRef.current
      if (stage) {
        const shown = Math.round(deg)
        if (stage.dataset.deg !== String(shown)) {
          stage.dataset.deg = String(shown)
          stage.setAttribute('aria-valuenow', String(shown))
          stage.setAttribute('aria-valuetext', `${shown} degrees`)
        }
      }
      const leds = ledsRef.current?.children
      if (leds) {
        const mag = Math.abs(deg)
        for (let i = 0; i < leds.length; i++) {
          const dist = Math.abs(i - (leds.length - 1) / 2)
          const on = bus.reduced ? dist < 1.2 : mag + 8 > dist * 8
          ;(leds[i] as HTMLElement).style.opacity = on ? '1' : '0.2'
        }
      }

      const moving = bus.pointer || Math.abs(bus.scroll) > 0.002 || !bus.reduced
      if (inView.current && moving) apiRef.current?.()
      frame = requestAnimationFrame(loop)
    }
    frame = requestAnimationFrame(loop)
    return () => cancelAnimationFrame(frame)
  }, [])

  const steerFrom = (clientX: number, clientY: number, rect: DOMRect) => {
    const nx = ((clientX - rect.left) / rect.width - 0.5) * 2
    const ny = ((clientY - rect.top) / rect.height - 0.5) * 2
    const bus = busRef.current
    bus.target = clamp(nx * 0.95, -1.05, 1.05)
    bus.pointer = true
    bus.brake = clamp(ny * 1.3, 0, 1)
    bus.throttle = clamp(-ny * 1.3, 0, 1)
  }

  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    event.currentTarget.setPointerCapture(event.pointerId)
    steerFrom(event.clientX, event.clientY, event.currentTarget.getBoundingClientRect())
  }

  const onPointerMove = (event: ReactPointerEvent<HTMLDivElement>) => {
    const captured = event.currentTarget.hasPointerCapture(event.pointerId)
    if (event.pointerType === 'touch' && !captured) return
    steerFrom(event.clientX, event.clientY, event.currentTarget.getBoundingClientRect())
  }

  const release = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId)
    }
    busRef.current.pointer = false
  }

  const onKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    const bus = busRef.current
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight' && event.key !== 'Home') return
    event.preventDefault()
    if (!bus.pointer) bus.target = bus.current
    bus.pointer = true
    if (event.key === 'ArrowLeft') bus.target = clamp(bus.target - 0.14, -1.05, 1.05)
    if (event.key === 'ArrowRight') bus.target = clamp(bus.target + 0.14, -1.05, 1.05)
    if (event.key === 'Home') {
      bus.pointer = false
      bus.target = 0
    }
  }

  return (
    <section className="hero-section" id="top" ref={sectionRef}>
      <div className="wrap hero">
        <div className="hero-copy">
          <p className="kicker">
            <i className="live" aria-hidden="true" />
            v0.2.0 · Android + Windows
          </p>
          <h1>
            The phone
            <br />
            is the <span>wheel.</span>
          </h1>
          <p className="lede">
            Roll it to steer. Thumbs stay on the buttons. The edges of the screen are the pedals.
          </p>
          <div className="cta">
            <a className="btn btn-red" href={APK}>
              Download APK
            </a>
            <a className="btn btn-ghost" href={EXE}>
              Windows receiver
            </a>
          </div>
          <p className="release-line">
            <a href={RELEASE}>Release v0.2.0</a>
            <span aria-hidden="true"> · </span>
            GPL-3.0
          </p>
          <dl className="stats">
            <div>
              <dt>Link</dt>
              <dd>100 Hz</dd>
            </div>
            <div>
              <dt>Buttons</dt>
              <dd>1–32</dd>
            </div>
            <div>
              <dt>Lock</dt>
              <dd>1080°</dd>
            </div>
          </dl>
        </div>

        <div className="hero-stage">
          <div
            className={showGl && wheelReady ? 'wheel-stage is-ready' : 'wheel-stage'}
            ref={stageRef}
            role="slider"
            tabIndex={0}
            aria-label="Steering preview. Drag, or use the left and right arrow keys."
            aria-valuemin={-60}
            aria-valuemax={60}
            aria-valuenow={0}
            aria-valuetext="0 degrees"
            onPointerDown={onPointerDown}
            onPointerMove={onPointerMove}
            onPointerUp={release}
            onPointerCancel={release}
            onPointerLeave={(event) => {
              if (!event.currentTarget.hasPointerCapture(event.pointerId)) busRef.current.pointer = false
            }}
            onKeyDown={onKeyDown}
            onBlur={() => {
              busRef.current.pointer = false
            }}
          >
            <img
              ref={fallbackRef}
              className="wheel-fallback"
              src={`${import.meta.env.BASE_URL}wheel-fallback.svg`}
              alt={showGl ? '' : 'Stylised formula racing wheel'}
            />
            {showGl ? (
              <div className="wheel-gl">
                <WheelBoundary onError={() => setFailed(true)}>
                  <Suspense fallback={null}>
                    <WheelScene bus={busRef} apiRef={apiRef} onReady={onWheelReady} />
                  </Suspense>
                </WheelBoundary>
              </div>
            ) : null}
          </div>

          <div className="cluster" aria-hidden="true">
            <div className="mini-leds" ref={ledsRef}>
              {Array.from({ length: 15 }, (_, i) => (
                <span key={i} />
              ))}
            </div>
            <div className="readouts">
              <p>
                <span className="lbl">Steer</span>
                <span className="val" ref={steerEl}>
                  0°
                </span>
              </p>
              <p>
                <span className="lbl">Brake</span>
                <span className="meter meter-brake">
                  <i ref={brakeEl} />
                </span>
              </p>
              <p>
                <span className="lbl">Throttle</span>
                <span className="meter meter-throttle">
                  <i ref={throttleEl} />
                </span>
              </p>
            </div>
          </div>
          <p className="hint">Drag to steer. Scrolling turns the wheel. Arrow keys work when it is focused.</p>
        </div>
      </div>

      <div className="ticker" aria-hidden="true">
        <div className="ticker-track">
          {[0, 1].map((copy) => (
            <div className="ticker-group" key={copy}>
              {TICKER.map((item) => (
                <span key={`${copy}-${item}`}>{item}</span>
              ))}
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}

class WheelBoundary extends Component<{ children: ReactNode; onError: () => void }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  componentDidCatch() {
    this.props.onError()
  }

  render() {
    if (this.state.failed) return null
    return this.props.children
  }
}
