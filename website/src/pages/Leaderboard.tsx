import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import Footer from '../components/Footer'
import Header from '../components/Header'
import { api, formatGap, formatLap } from '../lib/trace'

type Track = { folder: string; name: string; layouts: Array<{ id: string; name: string }> }
type Car = { id: string; name: string }
type Entry = { position: number; driver: string; timeMs: number; gapMs: number; sessionId: string }

export default function LeaderboardPage() {
  const [tracks, setTracks] = useState<Track[]>([])
  const [cars, setCars] = useState<Car[]>([])
  const [track, setTrack] = useState('monza')
  const [layout, setLayout] = useState('')
  const [car, setCar] = useState('ks_ferrari_488_gt3')
  const [customCar, setCustomCar] = useState('')
  const [entries, setEntries] = useState<Entry[]>([])
  const [label, setLabel] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all([api<{ tracks: Track[] }>('/api/tracks'), api<{ cars: Car[] }>('/api/cars')])
      .then(([trackBody, carBody]) => {
        setTracks(trackBody.tracks)
        setCars(carBody.cars)
      })
      .catch((err: Error) => setError(err.message))
  }, [])

  const layouts = useMemo(() => tracks.find((item) => item.folder === track)?.layouts ?? [], [tracks, track])
  const carId = customCar.trim() || car

  useEffect(() => {
    if (!track || !carId) return
    const params = new URLSearchParams({ track, layout, car: carId })
    api<{ entries: Entry[]; trackLabel: string; carLabel: string }>(`/api/leaderboard?${params}`)
      .then((body) => {
        setEntries(body.entries)
        setLabel(`${body.trackLabel} · ${body.carLabel}`)
      })
      .catch((err: Error) => setError(err.message))
  }, [track, layout, carId])

  return (
    <>
      <Header />
      <main className="coach-page">
        <div className="wrap">
          <p className="kicker">
            <span>02</span> Leaderboard
          </p>
          <h1>
            Best <span>lap</span>
          </h1>
          <p className="lede">One row per driver, for a track, layout, and car. Official Assetto Corsa folders, plus any mod car you type.</p>
          {error ? <p className="form-error">{error}</p> : null}
          <div className="filters">
            <label>
              Track
              <select
                value={track}
                onChange={(event) => {
                  const next = event.target.value
                  setTrack(next)
                  const first = tracks.find((item) => item.folder === next)?.layouts[0]
                  setLayout(first?.id ?? '')
                }}
              >
                {tracks.map((item) => (
                  <option key={item.folder} value={item.folder}>
                    {item.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Layout
              <select value={layout} onChange={(event) => setLayout(event.target.value)}>
                {layouts.map((item) => (
                  <option key={item.id || 'default'} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Car
              <select value={car} onChange={(event) => setCar(event.target.value)}>
                {cars.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Custom car folder
              <input value={customCar} onChange={(event) => setCustomCar(event.target.value)} placeholder="optional folder" />
            </label>
          </div>
          <section className="panel">
            <h2>{label || 'Times'}</h2>
            <table className="sheet">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Driver</th>
                  <th>Lap</th>
                  <th>Gap</th>
                </tr>
              </thead>
              <tbody>
                {entries.length === 0 ? (
                  <tr>
                    <td colSpan={4}>No times for this combination yet.</td>
                  </tr>
                ) : null}
                {entries.map((entry) => (
                  <tr key={`${entry.position}-${entry.driver}`}>
                    <td>{entry.position}</td>
                    <td>{entry.driver}</td>
                    <td>
                      <Link to={`/session/${entry.sessionId}`}>{formatLap(entry.timeMs)}</Link>
                    </td>
                    <td>{entry.position === 1 ? '—' : formatGap(entry.gapMs)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        </div>
      </main>
      <Footer />
    </>
  )
}
