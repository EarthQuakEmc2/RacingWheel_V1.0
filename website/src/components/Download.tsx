import { telegramUrl } from '../lib/telegram'

const APK = 'https://github.com/EarthQuakEmc2/RacingWheel_V1.0/releases/download/v0.6.0/RacingWheelPro.apk'
const EXE = 'https://github.com/EarthQuakEmc2/RacingWheel_V1.0/releases/download/v0.6.0/RacingWheelPro-PC.exe'
const RELEASE = 'https://github.com/EarthQuakEmc2/RacingWheel_V1.0/releases/tag/v0.6.0'

export default function Download() {
  return (
    <section className="section" id="download">
      <div className="wrap">
        <header className="section-head">
          <p className="kicker">
            <span>04</span> Download
          </p>
          <h2>Phone app. Windows receiver.</h2>
        </header>
        <div className="download-grid">
          <article className="dl-card">
            <p className="dl-kicker">Android</p>
            <h3>RacingWheelPro.apk</h3>
            <p>Landscape, full screen, signed release build. Allow installs from your file manager when Android asks.</p>
            <a className="btn btn-red" href={APK}>
              Download APK
            </a>
          </article>
          <article className="dl-card dl-card-pc">
            <p className="dl-kicker">Windows 64-bit</p>
            <h3>RacingWheelPro-PC.exe</h3>
            <p>Self-contained .NET 8 build. Windows does not need a separate .NET install. ViGEmBus does the rest.</p>
            <a className="btn btn-ghost" href={EXE}>
              Download for Windows
            </a>
          </article>
        </div>
        <p className="release-row">
          <a href={RELEASE}>Android app and Windows receiver on the v0.6.0 release</a>
          <span>GPL-3.0 · 21 Nov 2026</span>
        </p>
        <p className="release-row">
          <a href={telegramUrl}>Join our Telegram</a>
          <span>Chat, updates, polls, and downloads</span>
        </p>
      </div>
    </section>
  )
}
