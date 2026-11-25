import LedStrip from './LedStrip'
import { telegramUrl } from '../lib/telegram'

export default function Footer() {
  return (
    <footer className="site-footer">
      <LedStrip />
      <div className="wrap footer-grid">
        <div>
          <p className="footer-brand">Racing Wheel Pro</p>
          <p>An Android wheel, a Windows receiver, and an Assetto Corsa coach. Version 0.6.0. Every wheel template is included.</p>
        </div>
        <div>
          <p className="footer-label">License</p>
          <p>
            <a href="https://www.gnu.org/licenses/gpl-3.0.html" target="_blank" rel="noreferrer">
              GPL-3.0
            </a>
          </p>
          <p>
            Based on{' '}
            <a href="https://github.com/thelv/touchracer" target="_blank" rel="noreferrer">
              Touch Racer
            </a>{' '}
            by thelv.
          </p>
        </div>
        <div>
          <p className="footer-label">Project</p>
          <p>
            <a href="https://github.com/EarthQuakEmc2/RacingWheel_V1.0" target="_blank" rel="noreferrer">
              GitHub
            </a>
          </p>
          <p>
            <a href="https://github.com/EarthQuakEmc2/RacingWheel_V1.0/releases/tag/v0.6.0">Release v0.6.0</a>
          </p>
          <p>
            <a href={telegramUrl} target="_blank" rel="noreferrer">
              Join our Telegram
            </a>
          </p>
          <p>
            <a href="/account">Account</a>
            {' · '}
            <a href="/leaderboard">Leaderboard</a>
            {' · '}
            <a href="/privacy">Privacy</a>
          </p>
        </div>
      </div>
      <div className="wrap footer-note">
        <p>
          Porsche, McLaren, Audi, BMW, Ferrari, Mercedes-AMG, Alfa Romeo, KTM, MOZA, and the other wheel names are
          trademarks of their owners. The pictures are templates for layout testing.
        </p>
      </div>
    </footer>
  )
}
