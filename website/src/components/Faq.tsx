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
    q: 'What is a Steering Package?',
    a: 'A one-time pack for one Assetto Corsa manufacturer, $3.99, or a bundle of any 3 ($9.99), any 6 ($14.99), any 9 ($24.99), or every manufacturer ($34.99). The phone downloads the templates after the server signs a licence. The four free wheels stay free. Class packs are listed until their prices are set. This branch checks out in sandbox mode and does not take a card.',
  },
  {
    q: 'What does the $3.99 unlock?',
    a: 'Racing Wheel Premium is a one-time purchase. It opens every wheel template and draws the dial pointer on every rotary. The free app already steers, brakes, throttles, edits layouts, and talks to the PC, and it keeps the pointer demo on one wheel.',
  },
  {
    q: 'Windows SmartScreen blocks the exe.',
    a: 'The receiver is an unsigned build from this repository. If you trust the file from the v0.5.0 release, choose More info, then Run anyway.',
  },
  {
    q: 'How do the paddle shifters reach the game?',
    a: 'The left edge of the wheel is shift up and the right edge is shift down. Each zone starts on the grip, at 20% of the wheel width. A button that crosses the zone keeps its circle, so that touch is the button. Resize either paddle in the editor. The receiver types Page Up and Page Down, and it holds vJoy buttons 31 and 32.',
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
            <span>06</span> Pit notes
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
