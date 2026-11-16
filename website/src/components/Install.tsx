import { useState } from 'react'

const VIGEM = 'https://github.com/nefarius/ViGEmBus/releases'
const ADB =
  'adb reverse tcp:41512 tcp:41512 && adb reverse tcp:41511 tcp:41511 && adb reverse tcp:41510 tcp:41510'

export default function Install() {
  const [copied, setCopied] = useState(false)

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(ADB)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1600)
    } catch {
      setCopied(false)
    }
  }

  return (
    <section className="section" id="install">
      <div className="wrap">
        <header className="section-head">
          <p className="kicker">
            <span>04</span> Install
          </p>
          <h2>Five steps to the grid.</h2>
        </header>
        <ol className="steps">
          <li>
            <span className="step-n">01</span>
            <div>
              <h3>Install the APK</h3>
              <p>Copy RacingWheelPro.apk onto the phone and open it. Allow installs from unknown sources when Android asks.</p>
            </div>
          </li>
          <li>
            <span className="step-n">02</span>
            <div>
              <h3>Install ViGEmBus, then restart Windows</h3>
              <p>
                Games only see a controller after the driver is in place. Download it from the{' '}
                <a href={VIGEM} target="_blank" rel="noreferrer">
                  ViGEmBus releases
                </a>{' '}
                and reboot if the installer asks.
              </p>
            </div>
          </li>
          <li>
            <span className="step-n">03</span>
            <div>
              <h3>Run the receiver</h3>
              <p>Start RacingWheelPro-PC.exe. The window should report ViGEm, not “monitor only”.</p>
              <pre className="terminal">
                <code>
                  Racing Wheel Pro receiver{'\n'}
                  {'  '}discovery UDP 41510   telemetry UDP 41511   TCP 41512{'\n'}
                  {'  '}output: <em>ViGEm Xbox 360</em>
                  {'\n'}
                  {'  '}Waiting for the phone.
                </code>
              </pre>
            </div>
          </li>
          <li>
            <span className="step-n">04</span>
            <div>
              <h3>Connect the phone</h3>
              <p>Same Wi-Fi as the PC. Open the app, wait for the machine to appear, and tap Connect.</p>
              <p>On a cable, turn on USB debugging, run the reverse forwards, then choose USB / ADB in the app.</p>
              <div className="codebar">
                <code>{ADB}</code>
                <button type="button" onClick={() => void copy()}>
                  {copied ? 'Copied' : 'Copy'}
                </button>
              </div>
            </div>
          </li>
          <li>
            <span className="step-n">05</span>
            <div>
              <h3>Bind Assetto Corsa</h3>
              <p className="callout">Start the receiver and connect the phone before you launch Assetto Corsa.</p>
              <p>In Controls, choose Wheel or Gamepad and pick the Xbox 360 controller.</p>
              <div className="binds" aria-label="Assetto Corsa bindings">
                <p>
                  <span>Steering</span>
                  <strong>Left stick X</strong>
                </p>
                <p>
                  <span>Throttle</span>
                  <strong>RT</strong>
                </p>
                <p>
                  <span>Brake</span>
                  <strong>LT</strong>
                </p>
              </div>
              <p>The same Xbox pad works in other sims that bind a gamepad.</p>
            </div>
          </li>
        </ol>
      </div>
    </section>
  )
}
