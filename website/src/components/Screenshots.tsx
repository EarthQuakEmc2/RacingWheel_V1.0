import { useRef, useState } from 'react'
import type { KeyboardEvent as ReactKeyboardEvent, PointerEvent as ReactPointerEvent } from 'react'
import driveFsr from '../../../docs/screenshots/drive-fsr.jpg'
import editorFsr from '../../../docs/screenshots/editor-fsr.jpg'
import setup from '../../../docs/screenshots/setup.jpg'
import connect from '../../../docs/screenshots/connect.jpg'
import hotspotsKs from '../../../docs/screenshots/hotspots-ks.jpg'
import hotspotsGs from '../../../docs/screenshots/hotspots-gs.jpg'

const SHOTS = [
  {
    src: driveFsr,
    title: 'Drive',
    detail: 'MOZA FSR',
    alt: 'Drive screen with the MOZA FSR wheel, a red brake zone on the left, and a green throttle zone on the right.',
  },
  {
    src: editorFsr,
    title: 'Layout editor',
    detail: 'Hotspot rings',
    alt: 'Layout editor showing coloured rings on every button and knob of the FSR wheel.',
  },
  {
    src: setup,
    title: 'Steering setup',
    detail: 'Range and curves',
    alt: 'Steering setup with rotation range at 180 degrees, sensitivity, dead zone, linearity, smoothing, sensor, and zero reference.',
  },
  {
    src: connect,
    title: 'Link',
    detail: 'Wi-Fi discovery',
    alt: 'Link screen that found a PC named RIG-01, with a Connect button and the adb reverse command.',
  },
  {
    src: hotspotsKs,
    title: 'MOZA KS',
    detail: 'GT template',
    alt: 'MOZA KS wheel template with hotspot rings on the side buttons and the turbo, TC, and ABS knobs.',
  },
  {
    src: hotspotsGs,
    title: 'MOZA GS',
    detail: 'Five knobs',
    alt: 'MOZA GS wheel template with hotspot rings across the face buttons and five rotary knobs.',
  },
]

export default function Screenshots() {
  const [index, setIndex] = useState(0)
  const startX = useRef<number | null>(null)
  const shot = SHOTS[index]

  const go = (next: number) => {
    setIndex((next + SHOTS.length) % SHOTS.length)
  }

  const onKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'ArrowLeft') {
      go(index - 1)
      event.preventDefault()
    }
    if (event.key === 'ArrowRight') {
      go(index + 1)
      event.preventDefault()
    }
  }

  const onPointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    startX.current = event.clientX
  }

  const onPointerUp = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (startX.current === null) return
    const dx = event.clientX - startX.current
    startX.current = null
    if (dx > 48) go(index - 1)
    if (dx < -48) go(index + 1)
  }

  return (
    <section className="section" id="drive">
      <div className="wrap">
        <header className="section-head">
          <p className="kicker">
            <span>01</span> On the phone
          </p>
          <h2>Landscape. Full screen.</h2>
        </header>

        <div
          className="gallery"
          role="region"
          aria-roledescription="carousel"
          aria-label="App screenshots"
          tabIndex={0}
          onKeyDown={onKeyDown}
        >
          <p className="sr" aria-live="polite">
            {shot.title}, {shot.detail}. Slide {index + 1} of {SHOTS.length}.
          </p>
          <div className="device" onPointerDown={onPointerDown} onPointerUp={onPointerUp}>
            <div className="device-screen">
              <img src={shot.src} alt={shot.alt} width={1600} height={900} />
            </div>
            <div className="device-caption">
              <span>{shot.title}</span>
              <span>{shot.detail}</span>
            </div>
          </div>
          <div className="gallery-controls">
            <button type="button" onClick={() => go(index - 1)} aria-label="Previous screenshot">
              <Chevron dir="left" />
            </button>
            <div className="dots" role="tablist" aria-label="Choose a screenshot">
              {SHOTS.map((item, i) => (
                <button
                  key={item.title}
                  type="button"
                  role="tab"
                  aria-selected={i === index}
                  aria-label={`${item.title}, ${item.detail}`}
                  className={i === index ? 'is-on' : undefined}
                  onClick={() => setIndex(i)}
                />
              ))}
            </div>
            <button type="button" onClick={() => go(index + 1)} aria-label="Next screenshot">
              <Chevron dir="right" />
            </button>
          </div>
          <div className="thumbs">
            {SHOTS.map((item, i) => (
              <button
                key={item.title}
                type="button"
                className={i === index ? 'is-on' : undefined}
                onClick={() => setIndex(i)}
                aria-label={`Show ${item.title}`}
              >
                <img src={item.src} alt="" width={1600} height={900} />
                <span>{item.title}</span>
              </button>
            ))}
          </div>
        </div>
        <p className="fine">
          MOZA wheel images are layout templates. MOZA is a trademark of its owner. This is not a MOZA product.
        </p>
      </div>
    </section>
  )
}

function Chevron({ dir }: { dir: 'left' | 'right' }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className="chevron">
      <path
        d={dir === 'left' ? 'M14 5 L7 12 L14 19' : 'M10 5 L17 12 L10 19'}
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
