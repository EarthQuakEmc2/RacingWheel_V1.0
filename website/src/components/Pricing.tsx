const FREE = [
  'Four wheels, one from each of the sets people ask for first',
  'Steering, throttle, brake, and an optional clutch',
  'Every button mode, and the layout editor',
  'The Windows receiver, over Wi-Fi or USB',
  'A live dial pointer on one wheel, so the knob demo is in the free app',
]

const PREMIUM = [
  'Every wheel template in the app',
  'The dial pointer on every rotary, snapping to the printed marks',
  'One purchase. No subscription and no ads',
  'Restore it on another phone from the same Google account',
]

export default function Pricing() {
  return (
    <section className="section" id="pricing">
      <div className="wrap">
        <header className="section-head">
          <p className="kicker">
            <span>03</span> Free vs Premium
          </p>
          <h2>Drive free. Unlock the garage once.</h2>
        </header>
        <div className="price-grid">
          <article className="price-card">
            <p className="dl-kicker">Free</p>
            <h3>$0</h3>
            <p>The open build. Core driving stays intact if you never pay.</p>
            <ul>
              {FREE.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </article>
          <article className="price-card price-card-premium">
            <p className="dl-kicker">Racing Wheel Premium</p>
            <h3>
              $3.99 <span>once</span>
            </h3>
            <p>A one-time in-app purchase on Google Play. Product id racing_wheel_premium.</p>
            <ul>
              {PREMIUM.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </article>
        </div>
        <p className="fine">
          The Play listing uses generic wheel names. Car makers and wheel makers have not endorsed this project, and
          their names are trademarks. Manufacturer templates are also sold as{' '}
          <a href="/packages">Steering Packages</a>. See the <a href="/privacy">privacy policy</a>.
        </p>
      </div>
    </section>
  )
}
