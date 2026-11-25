import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Footer from '../components/Footer'
import Header from '../components/Header'
import { bundle, catalogue, formatMoney } from '../data/catalogue'
import { addLine } from '../lib/cart'

export default function BundlePickerPage() {
  const { id = '' } = useParams()
  const found = bundle(id)
  const navigate = useNavigate()
  const [picked, setPicked] = useState<string[]>([])
  const count = found?.count ?? 0
  const ready = useMemo(() => {
    if (!found) return false
    if (found.kind === 'all') return true
    return picked.length === count
  }, [found, picked.length, count])

  if (!found) {
    return (
      <>
        <Header />
        <main className="coach-page">
          <div className="wrap">
            <h1>Unknown bundle</h1>
            <Link to="/packages">Back to the catalogue</Link>
          </div>
        </main>
        <Footer />
      </>
    )
  }

  function toggle(manufacturerId: string) {
    setPicked((current) => {
      if (current.includes(manufacturerId)) return current.filter((item) => item !== manufacturerId)
      if (current.length >= count) return current
      return [...current, manufacturerId]
    })
  }

  return (
    <>
      <Header />
      <main className="coach-page">
        <div className="wrap">
          <p className="kicker">
            <Link to="/packages">Steering Packages</Link>
          </p>
          <h1>{found.name}</h1>
          <p className="lede">
            {found.summary} {formatMoney(found.priceCents)}.
          </p>
          {found.kind === 'pick' ? (
            <>
              <p>
                Choose {count}. {picked.length} selected.
              </p>
              <ul className="pick-grid">
                {catalogue.manufacturers.map((pack) => {
                  const on = picked.includes(pack.id)
                  const disabled = !on && picked.length >= count
                  return (
                    <li key={pack.id}>
                      <label className={on ? 'pick on' : 'pick'}>
                        <input type="checkbox" checked={on} disabled={disabled} onChange={() => toggle(pack.id)} />
                        <span>{pack.name}</span>
                        <em>{pack.templates.length ? `${pack.templates.length} templates` : 'Content later'}</em>
                      </label>
                    </li>
                  )
                })}
              </ul>
            </>
          ) : (
            <p>This adds all {catalogue.manufacturers.length} manufacturer packages. Class packs stay separate.</p>
          )}
          <div className="cta">
            <button
              className="btn btn-red"
              type="button"
              disabled={!ready}
              onClick={() => {
                addLine({ type: 'bundle', id: found.id, manufacturers: found.kind === 'all' ? [] : picked })
                navigate('/cart')
              }}
            >
              Add bundle to cart · {formatMoney(found.priceCents)}
            </button>
            <Link className="btn btn-ghost" to="/packages">
              Catalogue
            </Link>
          </div>
        </div>
      </main>
      <Footer />
    </>
  )
}
