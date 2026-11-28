import { Link, useNavigate, useParams } from 'react-router-dom'
import Footer from '../components/Footer'
import Header from '../components/Header'
import { anyPack, catalogue, contentLabel, formatMoney, previewImage, purchasable } from '../data/catalogue'
import { addLine } from '../lib/cart'

export default function PackageDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const pack = anyPack(id)
  if (!pack) {
    return (
      <>
        <Header />
        <main className="coach-page">
          <div className="wrap">
            <h1>Unknown package</h1>
            <Link to="/packages">Back to the catalogue</Link>
          </div>
        </main>
        <Footer />
      </>
    )
  }
  const image = previewImage(pack)
  const sale = purchasable(pack)
  const kind = catalogue.classes.some((item) => item.id === pack.id) ? 'class' : 'manufacturer'
  return (
    <>
      <Header />
      <main className="coach-page">
        <div className="wrap pack-detail">
          <p className="kicker">
            <Link to="/packages">Steering Packages</Link>
          </p>
          <h1>{pack.name}</h1>
          <p className="lede">{pack.summary}</p>
          <div className="pack-detail-grid">
            <div className="panel">
              {image ? <img src={image} alt="" /> : <p>No wheel picture in this build yet.</p>}
            </div>
            <div className="panel">
              <p className={sale ? 'pack-price' : 'pack-price is-todo'}>{formatMoney(pack.priceCents, pack.priceStatus)}</p>
              <p>{contentLabel(pack)}</p>
              {pack.includedFree.length > 0 ? (
                <p>Already free, and not locked by this pack: {pack.includedFree.join(', ')}.</p>
              ) : null}
              {sale ? (
                <button
                  className="btn btn-red"
                  type="button"
                  onClick={() => {
                    addLine({ type: kind, id: pack.id })
                    navigate('/cart')
                  }}
                >
                  Add to cart
                </button>
              ) : (
                <button className="btn btn-ghost" type="button" disabled>
                  Price TODO
                </button>
              )}
              <p className="fine">The app installs the templates after the server signs a licence. Editing that file does not add a wheel.</p>
            </div>
          </div>
          {pack.templates.length > 0 ? (
            <section className="panel">
              <h2>Templates this pack unlocks</h2>
              <ul className="pack-templates">
                {pack.templates.map((template) => (
                  <li key={template}>{template}</li>
                ))}
              </ul>
            </section>
          ) : (
            <section className="panel">
              <h2>Pack content</h2>
              <p>Nothing to download yet. Add template ids to the catalogue, then hash the files. A purchase still records the entitlement for the plates added later.</p>
            </section>
          )}
        </div>
      </main>
      <Footer />
    </>
  )
}
