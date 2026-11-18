const ITEMS = [
  {
    q: 'The console says “monitor only”.',
    a: 'ViGEmBus is missing, or Windows has not been restarted since the installer ran. Install the driver, reboot, and start RacingWheelPro-PC.exe again. The line should read output: ViGEm Xbox 360.',
  },
  {
    q: 'The game does not list a controller.',
    a: 'Press Win+R and open joy.cpl. An Xbox 360 controller appears only while the receiver is running with ViGEm. Connect the phone before you launch Assetto Corsa, then pick that controller under Controls.',
  },
  {
    q: 'The phone never finds the PC.',
    a: 'Both devices need the same Wi-Fi, and the receiver has to be started first. If discovery stays empty, type the PC’s IPv4 address and use Connect IP. The ports are UDP 41510, UDP 41511, and TCP 41512.',
  },
  {
    q: 'Can this run over a USB cable?',
    a: 'Yes. With USB debugging on, reverse ports 41512, 41511, and 41510, then tap USB / ADB. The app talks to 127.0.0.1 and adb forwards those ports to the PC.',
  },
  {
    q: 'Windows SmartScreen blocks the exe.',
    a: 'The receiver is an unsigned build from this repository. If you trust the file from the v0.4.0 release, choose More info, then Run anyway.',
  },
  {
    q: 'How does the driving coach link to my PC?',
    a: 'Start the Windows receiver and read the pairing code in its Logs window (or at http://127.0.0.1:41513). Sign in at myracingwheel.site/account and enter that code. Turn on automatic upload if you want finished sessions sent up. The coach reads per-corner metrics, not the raw trace.',
  },
]

export default function Faq() {
  return (
    <section className="section section-tight" id="notes">
      <div className="wrap faq-wrap">
        <header className="section-head">
          <p className="kicker">
            <span>05</span> Pit notes
          </p>
          <h2>When the pad is missing.</h2>
        </header>
        <div className="faq">
          {ITEMS.map((item, index) => (
            <details key={item.q} open={index === 0}>
              <summary>{item.q}</summary>
              <p>{item.a}</p>
            </details>
          ))}
        </div>
      </div>
    </section>
  )
}
