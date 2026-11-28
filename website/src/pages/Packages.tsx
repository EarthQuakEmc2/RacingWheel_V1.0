import { Link } from 'react-router-dom'
import Footer from '../components/Footer'
import Header from '../components/Header'
import { catalogue, contentLabel, formatMoney, previewImage, purchasable, type CataloguePack } from '../data/catalogue'

export default function PackagesPage() {
  return (
    <>
      <Header />
      <main className="coach-page">
        <div className="wrap">
          <p className="kicker">
            <span>04</span> Steering Packages
          </p>
          <h1>
            Buy the <span>garage</span> by maker
          </h1>
          <p className="lede">
            Each manufacturer is {formatMoney(catalogue.manufacturerPriceCents)}. A bundle is cheaper than buying the
            same makers one by one. Prices come from one catalogue file.
          </p>
          <p className="pack-tools">
            <Link className="btn btn-ghost" to="/cart">
              Cart
            </Link>
          </p>
          <h2>Bundles</h2>
          <div className="pack-bundle-row">
            {catalogue.bundles.map((item) => (
              <Link key={item.id} className="pack-bundle" to={`/packages/bundle/${item.id}`}>
                <span>{item.name}</span>
                <strong>{formatMoney(item.priceCents)}</strong>
                <em>{item.summary}</em>
              </Link>
            ))}
          </div>
          <h2>Manufacturers</h2>
          <ul className="pack-grid">
            {catalogue.manufacturers.map((pack) => (
              <PackCard key={pack.id} pack={pack} />
            ))}
          </ul>
          <h2>Classes</h2>
          <p className="fine">Formula 1, GT1, GT2, GT3, GT4, GT5, M1, M2, M3, and M4. A TODO price cannot be checked out.</p>
          <ul className="pack-grid">
            {catalogue.classes.map((pack) => (
              <PackCard key={pack.id} pack={pack} />
            ))}
          </ul>
          <p className="legal-note">
            Car and wheel names are trademarks of their owners. These packs are layout templates. They are not affiliated
            with, or endorsed by, those owners or Kunos Simulazioni.
          </p>
        </div>
      </main>
      <Footer />
    </>
  )
}

function PackCard({ pack }: { pack: CataloguePack }) {
  const image = previewImage(pack)
  const price = formatMoney(pack.priceCents, pack.priceStatus)
  return (
    <li>
      <Link to={`/packages/${pack.id}`}>
        {image ? <img src={image} alt="" /> : <span className="pack-empty">No plate yet</span>}
        <strong>{pack.name}</strong>
        <span>{contentLabel(pack)}</span>
        <em className={purchasable(pack) ? '' : 'is-todo'}>{price}</em>
      </Link>
    </li>
  )
}
