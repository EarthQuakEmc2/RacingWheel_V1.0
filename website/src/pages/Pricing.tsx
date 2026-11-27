import { Fragment } from 'react'
import Footer from '../components/Footer'
import Header from '../components/Header'
import { WHEEL_PACKS, type WheelPack } from '../data/packs'

export default function PricingPage() {
  return (
    <>
      <a className="skip" href="#content">
        Skip to content
      </a>
      <Header />
      <main id="content" className="pack-page">
        <div className="wrap">
          <header className="section-head">
            <p className="kicker">
              <span>03</span> Store
            </p>
            <h1>
              Steering wheel <span>packs</span>
            </h1>
            <p className="lede">
              Ten Assetto Corsa steering wheel packs for Racing Wheel Pro. Version 1.0.01 stays free. Each pack is USD$ 3.99.
            </p>
          </header>
          <div className="pack-list">
            {WHEEL_PACKS.map((pack, index) => (
              <PackCard key={pack.id} pack={pack} eager={index === 0} />
            ))}
          </div>
          <p className="fine">
            Add to Cart opens Stripe Checkout in a new tab. Version 1.0.01 does not require a pack. Car makers have not
            endorsed this project, and their names are trademarks.
          </p>
        </div>
      </main>
      <Footer />
    </>
  )
}

function PackCard({ pack, eager }: { pack: WheelPack; eager: boolean }) {
  return (
    <article className="pack-card">
      <figure className="pack-banner">
        <img
          src={pack.banner}
          alt={pack.bannerAlt}
          width={2943}
          height={800}
          loading={eager ? 'eager' : 'lazy'}
          decoding="async"
        />
      </figure>
      <div className="pack-buy">
        <p className="pack-pill">{pack.tag}</p>
        <div className="pack-copy">
          <h2>{pack.title}</h2>
          <p className="pack-sub">{pack.subtitle}</p>
          {pack.promoLabel ? <p className="pack-promo">{pack.promoLabel}</p> : null}
        </div>
        <p className="pack-platforms" aria-label={`${pack.platforms.join(' and ')}. ${pack.compatibility}.`}>
          {pack.platforms.map((name, index) => (
            <Fragment key={name}>
              {index > 0 ? (
                <span className="pack-plus" aria-hidden="true">
                  +
                </span>
              ) : null}
              <span className="pack-platform">
                <PlatformIcon name={name} />
                {name}
              </span>
            </Fragment>
          ))}
          <span className="pack-plat-rule" aria-hidden="true" />
          <span className="pack-compat">{pack.compatibility}</span>
        </p>
        <PackAction pack={pack} />
      </div>
    </article>
  )
}

function PackAction({ pack }: { pack: WheelPack }) {
  const onSale = Boolean(pack.promoPrice)
  return (
    <div className="pack-action">
      {typeof pack.discountPercent === 'number' ? (
        <span className="pack-discount">-{pack.discountPercent}%</span>
      ) : null}
      <p className="pack-price">
        {onSale ? <s className="pack-price-was">{pack.price}</s> : null}
        <span className="pack-price-now">{onSale ? pack.promoPrice : pack.price}</span>
      </p>
      <a className="pack-cart" href={pack.checkoutUrl} target="_blank" rel="noopener noreferrer">
        Add to Cart
        <span className="sr">, {pack.brand} Pack</span>
      </a>
    </div>
  )
}

function PlatformIcon({ name }: { name: string }) {
  if (name === 'Android') return <AndroidMark />
  if (name === 'Windows') return <WindowsMark />
  return null
}

function AndroidMark() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="currentColor"
        d="M7.2 8.7 5.9 6.4a.7.7 0 0 1 1.2-.7l1.3 2.2A7.6 7.6 0 0 1 12 7c1.2 0 2.4.3 3.6.9l1.3-2.2a.7.7 0 1 1 1.2.7L16.8 8.7A6.8 6.8 0 0 1 19 13.5H5a6.8 6.8 0 0 1 2.2-4.8ZM9 12.2a1 1 0 1 0 0-2 1 1 0 0 0 0 2Zm6 0a1 1 0 1 0 0-2 1 1 0 0 0 0 2ZM6 14.5h1.2V18a1.2 1.2 0 0 1-2.4 0v-2.3A6.7 6.7 0 0 1 6 14.5Zm12 0c.4.4.8.9 1.2 1.5V18a1.2 1.2 0 0 1-2.4 0v-3.5H18Z"
      />
    </svg>
  )
}

function WindowsMark() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <path
        fill="currentColor"
        d="M1 2.6 6.8 1.8v5.5H1V2.6Zm6.6-.9L15 .6v6.7H7.6V1.7ZM1 8.7h5.8v5.5L1 13.4V8.7Zm6.6 0H15v6.7l-7.4-1.1V8.7Z"
      />
    </svg>
  )
}
