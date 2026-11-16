const COLORS = [
  '#1f8a3b',
  '#2ebd4e',
  '#3ddc57',
  '#8ef24a',
  '#c6f542',
  '#e7ef3a',
  '#f5c518',
  '#f5c518',
  '#f0a202',
  '#e45700',
  '#e10600',
  '#ff2a2a',
  '#e10600',
  '#ff2d55',
  '#e040fb',
  '#c026d3',
  '#a855f7',
  '#7c5cff',
  '#2f80ed',
  '#3d8bfd',
  '#00b3ff',
  '#00e5ff',
]

export default function LedStrip({ animate = false }: { animate?: boolean }) {
  const leds = [...COLORS, ...COLORS]
  return (
    <div className={animate ? 'led-strip is-animated' : 'led-strip'} aria-hidden="true">
      {leds.map((color, i) => (
        <span key={i} style={{ background: color }} />
      ))}
    </div>
  )
}
