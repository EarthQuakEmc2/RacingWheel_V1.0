import { useState } from 'react'
import { BRAND_ORDER, WHEELS } from '../data/wheels'

export default function Manufacturers() {
  const [brand, setBrand] = useState<(typeof BRAND_ORDER)[number]>('Porsche')
  const wheels = WHEELS[brand] ?? []

  return (
    <section className="section" id="makers">
      <div className="wrap">
        <header className="section-head">
          <p className="kicker">
            <span>02</span> Garage
          </p>
          <h2>Wheels by manufacturer.</h2>
          <p>The same brand tabs as the phone. Empty makers stay on the list so the next wheel is a JSON file away.</p>
        </header>
        <div className="maker-tabs" role="tablist" aria-label="Manufacturers">
          {BRAND_ORDER.map((name) => (
            <button
              key={name}
              type="button"
              role="tab"
              aria-selected={name === brand}
              className={name === brand ? 'maker-tab on' : 'maker-tab'}
              onClick={() => setBrand(name)}
            >
              {name}
            </button>
          ))}
        </div>
        {wheels.length === 0 ? (
          <div className="coming-soon">
            <p className="kicker">Coming soon</p>
            <p>No {brand} wheel in this build. Add a PNG and a template whose manufacturer field is {brand}.</p>
          </div>
        ) : (
          <ul className="wheel-gallery">
            {wheels.map((wheel) => (
              <li key={wheel.id}>
                <figure>
                  <img src={`${import.meta.env.BASE_URL}wheels/${wheel.image}`} alt={wheel.name} />
                  <figcaption>
                    <strong>{wheel.name}</strong>
                    <span>
                      {wheel.buttons} buttons
                      {wheel.rotaries > 0 ? ` · ${wheel.rotaries} rotaries` : ''}
                    </span>
                    <span className="wheel-blurb">{wheel.blurb}</span>
                  </figcaption>
                </figure>
              </li>
            ))}
          </ul>
        )}
        <p className="legal-note">
          Porsche, McLaren, Audi, BMW, Ferrari, Mercedes-AMG, Alfa Romeo, KTM, MOZA, Fanatec, Thrustmaster, Cube
          Controls, Ascher, and the wheel names and pictures are trademarks of their owners. They are templates for
          layout testing. This project is not affiliated with, or endorsed by, those owners.
        </p>
      </div>
    </section>
  )
}
