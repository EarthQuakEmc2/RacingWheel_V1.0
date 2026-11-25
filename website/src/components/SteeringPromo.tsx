import { Link } from 'react-router-dom'
import { catalogue, formatMoney } from '../data/catalogue'

export default function SteeringPromo() {
  return (
    <section className="section" id="packages">
      <div className="wrap">
        <header className="section-head">
          <p className="kicker">
            <span>04</span> Steering Packages
          </p>
          <h2>One manufacturer at a time, or a bundle.</h2>
          <p>
            The same idea as an Assetto Corsa car pack. $3.99 unlocks that maker&apos;s wheel templates. Pick three, six,
            or nine, or take every manufacturer.
          </p>
        </header>
        <div className="pack-bundle-row">
          {catalogue.bundles.map((item) => (
            <Link key={item.id} className="pack-bundle" to={item.kind === 'all' ? '/packages/bundle/everything' : `/packages/bundle/${item.id}`}>
              <span>{item.name}</span>
              <strong>{formatMoney(item.priceCents)}</strong>
            </Link>
          ))}
        </div>
        <p className="fine">
          Class packs (Formula 1, GT1–GT5, M1–M4) are listed with price TODO until they are priced.{' '}
          <Link to="/packages">Open the catalogue</Link>. The four free wheels stay free. Checkout on this branch is a
          sandbox: no card is collected.
        </p>
      </div>
    </section>
  )
}
