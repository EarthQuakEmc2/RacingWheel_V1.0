const FREE = [
  'Every wheel template in version 1.0.01',
  'Steering, throttle, brake, and an optional clutch',
  'Every button, rotary, and the layout editor',
  'The Windows receiver, over Wi-Fi or USB',
  'Shift lights, the live dash, and turning knobs',
]

const PREMIUM = [
  'Not required for any wheel in this release',
  'The paid Play build is on standby and is not merged',
  'No wheel is locked in the app or on this site',
]

export default function Pricing() {
  return (
    <section className="section" id="pricing">
      <div className="wrap">
        <header className="section-head">
          <p className="kicker">
            <span>03</span> Garage
          </p>
          <h2>Every wheel is included.</h2>
        </header>
        <div className="price-grid">
          <article className="price-card">
            <p className="dl-kicker">Free</p>
            <h3>$0</h3>
            <p>Version 1.0.01. The garage is open.</p>
            <ul>
              {FREE.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </article>
          <article className="price-card price-card-premium">
            <p className="dl-kicker">Paid build</p>
            <h3>Standby</h3>
            <p>A Play purchase is not part of 1.0.01. Wheels are not behind it.</p>
            <ul>
              {PREMIUM.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </article>
        </div>
        <div className="price-more">
          <p>
            Version 1.0.01 stays free, and every wheel template in that release is included. Optional Assetto Corsa
            steering wheel packs are listed separately.
          </p>
          <a className="btn btn-red" href="/pricing">
            Browse Steering Wheel Packs
          </a>
        </div>
        <p className="fine">
          The Play listing uses generic wheel names. Car makers and wheel makers have not endorsed this project, and
          their names are trademarks. The purchase is processed by Google Play. See the{' '}
          <a href="/privacy">privacy policy</a>.
        </p>
      </div>
    </section>
  )
}
