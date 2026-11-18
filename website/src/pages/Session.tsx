import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { PedalChart, SpeedChart, SteerChart, SteerSplit } from '../components/CoachCharts'
import Footer from '../components/Footer'
import Header from '../components/Header'
import { api, asSamples, formatLap, type Sample } from '../lib/trace'

type Corner = {
  name: string
  brakePointM: number | null
  peakBrake: number
  trailBrakeMs: number
  minSpeedKmh: number | null
  throttlePickupM: number | null
  steeringSmoothness: number
  coastingMs: number
  exitRpm: number
  exitGear: number
}

type Lap = { id: string; index: number; timeMs: number | null; valid: boolean; samples: Sample[]; corners: Corner[] }

type Payload = {
  session: {
    id: string
    trackLabel: string
    carLabel: string
    track_length_m: number | null
    source: string
    best_lap_ms: number | null
  }
  laps: Array<Omit<Lap, 'samples'> & { samples: unknown }>
  coach: { text?: string; source?: string; rules?: string[]; quotaExceeded?: boolean; cached?: boolean } | null
}

export default function SessionPage() {
  const { id = '' } = useParams()
  const [data, setData] = useState<Payload | null>(null)
  const [error, setError] = useState('')
  const [lapIndex, setLapIndex] = useState<number | null>(null)
  const [coach, setCoach] = useState<Payload['coach']>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api<Payload>(`/api/sessions/${id}`)
      .then((body) => {
        const laps = body.laps.map((lap) => ({ ...lap, samples: asSamples(lap.samples) }))
        setData({ ...body, laps })
        setCoach(body.coach)
        const best = laps.filter((lap) => lap.valid && lap.timeMs).sort((a, b) => (a.timeMs || 0) - (b.timeMs || 0))[0]
        setLapIndex((best && laps[laps.length - 1]?.index) || laps[0]?.index || null)
      })
      .catch((err: Error) => setError(err.message))
  }, [id])

  const laps = (data?.laps || []) as Lap[]
  const selected = laps.find((lap) => lap.index === lapIndex) || laps[laps.length - 1]
  const best = useMemo(() => laps.filter((lap) => lap.valid && lap.timeMs).sort((a, b) => (a.timeMs || 0) - (b.timeMs || 0))[0], [laps])
  const lengthM = data?.session.track_length_m || 1
  const compare = best && selected && best.id !== selected.id ? resampleTo(best.samples, selected.samples.length) : null

  return (
    <>
      <Header />
      <main className="coach-page">
        <div className="wrap">
          <p className="kicker">
            <span>01</span> Session
          </p>
          {error ? <p className="form-error">{error}</p> : null}
          {!data && !error ? <p className="lede">Loading the log…</p> : null}
          {data && selected ? (
            <>
              <h1>
                {data.session.trackLabel} <span>{formatLap(data.session.best_lap_ms)}</span>
              </h1>
              <p className="lede">
                {data.session.carLabel} · {data.session.source} · best {formatLap(data.session.best_lap_ms)}
              </p>
              <div className="lap-switch" role="tablist" aria-label="Laps">
                {laps.map((lap) => (
                  <button key={lap.id} type="button" className={lap.index === selected.index ? 'is-on' : ''} onClick={() => setLapIndex(lap.index)}>
                    Lap {lap.index} {formatLap(lap.timeMs)}
                    {lap.valid ? '' : ' invalid'}
                  </button>
                ))}
              </div>
              <div className="coach-grid">
                <section className="panel">
                  <h2>Throttle and brake</h2>
                  <div className="chart-box">
                    <PedalChart samples={selected.samples} lengthM={lengthM} compare={compare} />
                  </div>
                </section>
                <section className="panel">
                  <h2>Speed</h2>
                  <div className="chart-box">
                    <SpeedChart samples={selected.samples} lengthM={lengthM} compare={compare} />
                  </div>
                </section>
                <section className="panel">
                  <h2>Steering</h2>
                  <div className="chart-box">
                    <SteerChart samples={selected.samples} lengthM={lengthM} />
                  </div>
                </section>
                <section className="panel">
                  <h2>Left, straight, right</h2>
                  <div className="chart-box">
                    <SteerSplit samples={selected.samples} />
                  </div>
                </section>
              </div>
              <section className="panel">
                <h2>Corners</h2>
                <table className="sheet">
                  <thead>
                    <tr>
                      <th>Corner</th>
                      <th>Brake point</th>
                      <th>Peak brake</th>
                      <th>Trail</th>
                      <th>Min speed</th>
                      <th>Throttle</th>
                      <th>Smooth</th>
                      <th>Coast</th>
                    </tr>
                  </thead>
                  <tbody>
                    {selected.corners.length === 0 ? (
                      <tr>
                        <td colSpan={8}>No corners in this lap.</td>
                      </tr>
                    ) : null}
                    {selected.corners.map((corner) => (
                      <tr key={corner.name}>
                        <td>{corner.name}</td>
                        <td>{corner.brakePointM == null ? '—' : `${Math.round(corner.brakePointM)} m`}</td>
                        <td>{Math.round(corner.peakBrake * 100)}%</td>
                        <td>{Math.round(corner.trailBrakeMs)} ms</td>
                        <td>{corner.minSpeedKmh == null ? '—' : `${Math.round(corner.minSpeedKmh)} km/h`}</td>
                        <td>{corner.throttlePickupM == null ? '—' : `${Math.round(corner.throttlePickupM)} m`}</td>
                        <td>{Math.round(corner.steeringSmoothness)}</td>
                        <td>{Math.round(corner.coastingMs)} ms</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </section>
              <section className="panel">
                <h2>Coach</h2>
                <p>The model sees these corner numbers and the track and car. It does not see the raw trace. Free accounts get a few analyses a day.</p>
                <button
                  className="btn btn-red"
                  type="button"
                  disabled={busy}
                  onClick={async () => {
                    setBusy(true)
                    setError('')
                    try {
                      const body = await api<NonNullable<Payload['coach']>>(`/api/sessions/${id}/coach`, { method: 'POST', body: '{}' })
                      setCoach(body)
                    } catch (err) {
                      const extra = err as Error & { body?: Payload['coach'] }
                      if (extra.body?.rules) setCoach(extra.body)
                      setError(extra.message)
                    } finally {
                      setBusy(false)
                    }
                  }}
                >
                  {busy ? 'Analysing…' : 'Analyse this session'}
                </button>
                {coach?.text ? (
                  <div className="coach-note">
                    <p className="kicker">
                      <span>{coach.source === 'workers-ai' ? 'AI' : 'Rules'}</span>
                      {coach.cached ? ' cached' : ''}
                      {coach.quotaExceeded ? ' quota' : ''}
                    </p>
                    {coach.text.split('\n').filter(Boolean).map((line) => (
                      <p key={line}>{line}</p>
                    ))}
                  </div>
                ) : null}
              </section>
              <p>
                <Link to="/account">Back to sessions</Link>
              </p>
            </>
          ) : null}
        </div>
      </main>
      <Footer />
    </>
  )
}

function resampleTo(source: Sample[], count: number) {
  if (source.length === 0 || count === 0) return null
  const out: Sample[] = []
  for (let index = 0; index < count; index++) {
    const at = Math.min(source.length - 1, Math.floor((index * source.length) / count))
    out.push(source[at])
  }
  return out
}
