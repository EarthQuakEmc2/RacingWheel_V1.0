const FEATURES = [
  {
    title: 'Tilt steering',
    text: 'Game rotation, accelerometer, or a finger drag. Range, dead zone, linearity, smoothing, invert, center.',
    graphic: <SteerGraphic />,
  },
  {
    title: 'Wheels by maker',
    text: 'Porsche, McLaren, Ferrari, Mercedes, and MOZA, plus empty tabs for the next brands. About 4 to 18 buttons and knobs, mapped onto virtual buttons 1–32.',
    graphic: <TemplatesGraphic />,
  },
  {
    title: 'Pedal zones',
    text: 'Brake on the left, throttle on the right, clutch underneath when you want a third pedal.',
    graphic: <PedalsGraphic />,
  },
  {
    title: 'Layout editor',
    text: 'Drag, nudge, resize, add, delete. The hotspot stays under your thumb while the picture turns.',
    graphic: <EditorGraphic />,
  },
  {
    title: 'Find the PC',
    text: 'Wi-Fi discovery on the same network, a typed IP, or USB through adb reverse.',
    graphic: <LinkGraphic />,
  },
  {
    title: 'A pad the sim knows',
    text: 'ViGEmBus presents an Xbox 360 controller. vJoy is there for 32 buttons and eight axes.',
    graphic: <PadGraphic />,
  },
]

export default function Features() {
  return (
    <section className="section section-tight" id="wheels">
      <div className="wrap">
        <header className="section-head">
          <p className="kicker">
            <span>02</span> The wheel
          </p>
          <h2>Everything under your hands.</h2>
        </header>
        <div className="feature-grid">
          {FEATURES.map((feature) => (
            <article className="card" key={feature.title}>
              <div className="card-art">{feature.graphic}</div>
              <h3>{feature.title}</h3>
              <p>{feature.text}</p>
            </article>
          ))}
        </div>

        <div className="signal" aria-label="Signal path">
          <span>Phone</span>
          <i />
          <span>100 Hz</span>
          <i />
          <span>Receiver</span>
          <i />
          <span>ViGEm / vJoy</span>
          <i />
          <span>Assetto Corsa</span>
          <i />
          <span>Other sims</span>
        </div>
      </div>
    </section>
  )
}

function SteerGraphic() {
  return (
    <svg viewBox="0 0 320 140" role="img" aria-label="Phone tilted inside a steering arc, with four tuning bars">
      <path d="M40 108 A90 90 0 0 1 150 28" fill="none" stroke="#2c343d" strokeWidth="8" strokeLinecap="round" />
      <path d="M48 100 A78 78 0 0 1 138 36" fill="none" stroke="#f5c518" strokeWidth="3" strokeLinecap="round" />
      <g transform="translate(168 22) rotate(18 36 48)">
        <rect x="8" y="0" width="56" height="104" rx="8" fill="#10141a" stroke="#f3f1ec" strokeWidth="2" />
        <rect x="16" y="18" width="40" height="62" rx="3" fill="#0b0d10" />
        <circle cx="36" cy="49" r="10" fill="none" stroke="#3ddc57" strokeWidth="2" />
        <rect x="14" y="86" width="18" height="8" fill="#e10600" />
        <rect x="40" y="86" width="18" height="8" fill="#3ddc57" />
      </g>
      {[
        ['RNG', 0.82],
        ['DZ', 0.28],
        ['LIN', 0.55],
        ['SM', 0.4],
      ].map(([label, amount], i) => (
        <g key={String(label)} transform={`translate(250 ${18 + i * 28})`}>
          <text x="0" y="11" fill="#b4bcc4" fontFamily="IBM Plex Mono, monospace" fontSize="10">
            {label}
          </text>
          <rect x="32" y="4" width="36" height="6" rx="1" fill="#2c343d" />
          <rect x="32" y="4" width={36 * Number(amount)} height="6" fill="#e10600" />
        </g>
      ))}
    </svg>
  )
}

function TemplatesGraphic() {
  const wheels = [
    { name: 'FSR', buttons: '10+7' },
    { name: 'KS', buttons: '11+3' },
    { name: 'FSR2', buttons: '9+5' },
    { name: 'GS', buttons: '10+5' },
  ]
  return (
    <svg viewBox="0 0 320 140" role="img" aria-label="Four wheel templates: FSR, KS, FSR2, and GS">
      {wheels.map((wheel, i) => {
        const x = 18 + i * 78
        return (
          <g key={wheel.name} transform={`translate(${x} 16)`}>
            <rect x="0" y="0" width="68" height="86" rx="18" fill="#10141a" stroke="#3c4651" />
            <rect x="16" y="18" width="36" height="22" rx="3" fill="#0b0d10" stroke="#f5c518" strokeWidth="1" />
            <circle cx="14" cy="28" r="4" fill={i % 2 ? '#e040fb' : '#f5c518'} />
            <circle cx="54" cy="28" r="4" fill="#3ddc57" />
            <circle cx="22" cy="58" r="6" fill="none" stroke="#3ddc57" strokeWidth="2" />
            <circle cx="46" cy="58" r="6" fill="none" stroke="#e10600" strokeWidth="2" />
            <text x="34" y="112" textAnchor="middle" fill="#f3f1ec" fontFamily="Oswald, sans-serif" fontSize="13">
              {wheel.name}
            </text>
            <text x="34" y="126" textAnchor="middle" fill="#8b969f" fontFamily="IBM Plex Mono, monospace" fontSize="9">
              {wheel.buttons}
            </text>
          </g>
        )
      })}
    </svg>
  )
}

function PedalsGraphic() {
  return (
    <svg viewBox="0 0 320 140" role="img" aria-label="Red brake bar on the left and green throttle bar on the right">
      <rect x="36" y="16" width="70" height="108" rx="6" fill="#14181d" stroke="#2c343d" />
      <rect x="214" y="16" width="70" height="108" rx="6" fill="#14181d" stroke="#2c343d" />
      <rect x="44" y="70" width="54" height="46" fill="#e10600" />
      <rect x="222" y="36" width="54" height="80" fill="#3ddc57" />
      <text x="71" y="64" textAnchor="middle" fill="#f3f1ec" fontFamily="Oswald, sans-serif" fontSize="14">
        18
      </text>
      <text x="71" y="108" textAnchor="middle" fill="#fff" fontFamily="Oswald, sans-serif" fontSize="11">
        BRAKE
      </text>
      <text x="249" y="64" textAnchor="middle" fill="#07110a" fontFamily="Oswald, sans-serif" fontSize="14">
        62
      </text>
      <text x="249" y="100" textAnchor="middle" fill="#07110a" fontFamily="Oswald, sans-serif" fontSize="11">
        THROTTLE
      </text>
      <text x="160" y="78" textAnchor="middle" fill="#b4bcc4" fontFamily="IBM Plex Mono, monospace" fontSize="11">
        CLUTCH
      </text>
      <rect x="128" y="88" width="64" height="8" rx="2" fill="#2c343d" />
      <rect x="128" y="88" width="24" height="8" fill="#4c9dff" />
    </svg>
  )
}

function EditorGraphic() {
  return (
    <svg viewBox="0 0 320 140" role="img" aria-label="Wheel with editable hotspot rings, one of them selected">
      <circle cx="150" cy="70" r="48" fill="#10141a" stroke="#3c4651" strokeWidth="10" />
      <rect x="126" y="48" width="48" height="28" rx="3" fill="#0b0d10" stroke="#2c343d" />
      <circle cx="112" cy="52" r="7" fill="none" stroke="#f5c518" strokeWidth="2" />
      <circle cx="188" cy="56" r="7" fill="none" stroke="#e040fb" strokeWidth="2" />
      <circle cx="128" cy="96" r="9" fill="none" stroke="#3ddc57" strokeWidth="2" />
      <g>
        <circle cx="176" cy="98" r="11" fill="none" stroke="#f5c518" strokeWidth="2" />
        <rect x="164" y="86" width="6" height="6" fill="#f5c518" />
        <rect x="182" y="86" width="6" height="6" fill="#f5c518" />
        <rect x="164" y="104" width="6" height="6" fill="#f5c518" />
        <rect x="182" y="104" width="6" height="6" fill="#f5c518" />
      </g>
      <text x="248" y="58" fill="#f5c518" fontFamily="IBM Plex Mono, monospace" fontSize="12">
        DRAG
      </text>
      <text x="248" y="78" fill="#b4bcc4" fontFamily="IBM Plex Mono, monospace" fontSize="12">
        NUDGE
      </text>
      <text x="248" y="98" fill="#b4bcc4" fontFamily="IBM Plex Mono, monospace" fontSize="12">
        SAVE
      </text>
    </svg>
  )
}

function LinkGraphic() {
  return (
    <svg viewBox="0 0 320 140" role="img" aria-label="Phone discovering a PC over Wi-Fi at 8 milliseconds">
      <rect x="28" y="36" width="46" height="72" rx="6" fill="#10141a" stroke="#f3f1ec" strokeWidth="1.6" />
      <text x="51" y="78" textAnchor="middle" fill="#f5c518" fontFamily="Oswald, sans-serif" fontSize="11">
        PHONE
      </text>
      <line x1="86" y1="72" x2="200" y2="72" stroke="#2c343d" strokeWidth="2" strokeDasharray="4 5" />
      <circle className="packet" cx="120" cy="72" r="5" fill="#3ddc57" />
      <rect x="200" y="40" width="92" height="64" rx="6" fill="#14181d" stroke="#f5c518" />
      <text x="246" y="68" textAnchor="middle" fill="#f3f1ec" fontFamily="Oswald, sans-serif" fontSize="14">
        RIG-01
      </text>
      <text x="246" y="88" textAnchor="middle" fill="#3ddc57" fontFamily="IBM Plex Mono, monospace" fontSize="11">
        8 ms
      </text>
      <text x="140" y="58" textAnchor="middle" fill="#8b969f" fontFamily="IBM Plex Mono, monospace" fontSize="10">
        UDP 41511
      </text>
    </svg>
  )
}

function PadGraphic() {
  return (
    <svg viewBox="0 0 320 140" role="img" aria-label="Xbox-style pad with left stick, right trigger, and left trigger highlighted">
      <path
        d="M40 58 h70 l16 18 h68 l16-18 h70 v28 c0 22-16 36-40 36 h-28 l-22-16 h-56 l-22 16 h-28 c-24 0-40-14-40-36 z"
        fill="#14181d"
        stroke="#3c4651"
      />
      <circle cx="108" cy="70" r="16" fill="none" stroke="#f5c518" strokeWidth="3" />
      <circle cx="114" cy="66" r="5" fill="#f5c518" />
      <text x="78" y="112" fill="#f5c518" fontFamily="IBM Plex Mono, monospace" fontSize="10">
        LX STEER
      </text>
      <rect x="70" y="28" width="36" height="12" rx="3" fill="#e10600" />
      <rect x="214" y="28" width="36" height="12" rx="3" fill="#3ddc57" />
      <text x="88" y="24" textAnchor="middle" fill="#ff8d86" fontFamily="Oswald, sans-serif" fontSize="11">
        LT
      </text>
      <text x="232" y="24" textAnchor="middle" fill="#8ef5a0" fontFamily="Oswald, sans-serif" fontSize="11">
        RT
      </text>
      <circle cx="214" cy="62" r="5" fill="#3ddc57" />
      <circle cx="230" cy="76" r="5" fill="#e10600" />
      <circle cx="198" cy="76" r="5" fill="#4c9dff" />
      <circle cx="214" cy="90" r="5" fill="#f5c518" />
    </svg>
  )
}
