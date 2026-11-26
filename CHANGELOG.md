# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.6.3] - 2026-11-25

### Changed

- The Windows receiver shows a command window again. A short status block stays in place: version, address and ports, whether the phone is connected, and vJoy or ViGEm. The detail log stays in `%APPDATA%\RacingWheelPro\logs`. Closing the window or Ctrl+C exits. `versionName` is 0.6.3.
- The wheel is the same size in Drive and in Edit. It is drawn at twice the first 0.6.3 size. When that no longer fits, the rim runs off the screen and the centre stays visible. Android `versionCode` is 18.

### Added

- Master Wheel F1, a free formula wheel.

## [0.6.2] - 2026-11-25

Stable release. Same features as 0.6.0, with the white Master Wheel icon.

### Changed

- Android `versionCode` is 16, `versionName` is 0.6.2.
- The site download and `/api/health` point at 0.6.2.

## [0.6.2-preview] - 2026-11-25

Prerelease. The website is not deployed. v0.6.0 stays the stable download.

### Changed

- The launcher icon, the Play listing icon, and the website favicon are the white Master Wheel mark. Android `versionCode` is 15.

## [Unreleased]

### Fixed

- The site deploy is `npx wrangler deploy` from `worker/`, which sets content types. The Worker also replaces `application/octet-stream` on static files so the homepage is rendered.

### Added

- Nothing yet.

## [0.6.0] - 2026-11-25

Stable release. Every wheel is included. The paid Play build stays unmerged.

### Fixed

- Gear up and gear down now show up in Assetto Corsa while you bind controls. They are vJoy button 1 and vJoy button 2. Buttons that used to be 1 and 2 on a wheel moved to the next free numbers, so a face button is not the paddle. Page Up and Page Down still type.
- The right paddle lights when you hold gear down. Its zone now reaches the outer edge of the picture, where that flipper sits, and the held zone is drawn bright white.
- A button with a light in the middle flashes while you hold it.
- Every wheel picture is the same overall size.

### Added

- About, under Setup. It shows Racing Wheel Pro, the version name, the build number, the build date, myracingwheel.site, the Telegram channel, GPL-3.0, and credit to Touch Racer by thelv.

### Changed

- Android `versionCode` is 13, `versionName` is 0.6.0. No wheel is locked.
- The Windows receiver has no console window. It writes `%APPDATA%\RacingWheelPro\logs\receiver.log` (older copies are kept when the file grows) and sits in the tray. Right-click that icon to exit. It does not print a line for each button.
- The site download points at this release. `/api/health` reports 0.6.0.

## [0.6.0-preview] - 2026-11-22

Prerelease for testing. v0.5.0 stays the stable download on the website and at `releases/latest`. This build is not merged.

The wheel on the phone now behaves more like the wheel in your hands. Shift lights follow the pedal or the engine, the screen shows the car, and the knobs turn.

### Added

- Rev lights on the wheels that have a shift-light row. The lamps fill from the left as you press the throttle. The colors run green, then yellow, then red, then blue and purple, the same order as a real rim. The thin strip along the top of the app does the same thing.
- An RPM mode. Setup, then Pedals, then Rev lights. Choose throttle or RPM. RPM stays dark until the engine is at 75% of its maximum and is fully lit at the redline. If Assetto Corsa is not sending data, RPM mode uses the throttle so the lights still move.
- A screen on every wheel template. The rectangle sits on the wheel's own display, or on the open part of the face when the picture has no screen. It shows gear (R, N, then 1 and up), speed, RPM, the current lap, the gap to your best lap, race position, fuel percent, and the TC and ABS settings. "NO SIGNAL" means the PC has not sent a fresh packet.
- Live telemetry from Assetto Corsa. The Windows receiver already reads `acpmf_physics`, `acpmf_graphics`, and `acpmf_static`. It now sends a 40-byte `RWPT` packet back to the phone on the same UDP link, about 25 times a second. The packet carries rpm, maximum rpm, speed, gear, throttle, brake, TC and ABS settings, how hard TC and ABS are working, fuel percent, race position, the current, last, and best lap, the live delta, and the completed lap count. The phone forgets the screen if packets stop for about a second. An older phone build ignores the packet. An older receiver simply never sends it, and the lights stay on the throttle.
- Knobs that turn. Drag around a dial, or tap + and −, and the cap rotates to the next detent. The pointer lands on the number printed around the knob. Position 1 is the mark the artwork was drawn on. A 12-position knob steps 30 degrees each time.
- Thumb wheels and scroll wheels. The small encoders on the grips (the FSR pair, the SF1000 side encoders, the RSR pair, and the Mercedes page wheels) spin when you drag up or down. Up, toward the top of the wheel picture, is the next position. Down is the previous one. The cap rotates so you can see it move.
- Each step is one button press, which is what Assetto Corsa can bind. The + side (or a drag to the next mark, or a thumb drag upward) pulses the knob's increase button once. The − side pulses the decrease button once. The press lasts long enough for the game to see it. The absolute position is still in that knob's slot on the wire. vJoy puts slots 0, 1, 2, and 3 on axes Ry, Rz, Slider, and Dial. On a 12-position knob, 0 is the low end of the axis and 11 is the high end. In Assetto Corsa, bind the increase and decrease buttons (for example MOZA KS turbo is buttons 13 and 12). Use the axis only if you want the position itself.
- Unit tests for the light colors, the RPM window, the lap-time and delta text, the telemetry packet, the knob rotation, and the shared-memory field positions. GitHub Actions builds the Android app and the Windows receiver on every push and pull request.
- A startup splash on the phone and on the website. The bar moves only when a real check finishes. On the phone those checks are: an HTTPS request to `https://myracingwheel.site/api/health` (Android accepts the certificate with its normal trust store, or the splash says the certificate was rejected or the server could not be reached), the health JSON (`ok` and the version the server reports), a look in this app's preferences and cookie jar for a saved website sign-in (there is no sign-in stored unless one was saved; the browser's cookies are not visible), a comparison of this app's version with the version in that health response, and a short wait for a PC receiver that answers discovery. On the website the checks are: the browser's secure-context flag, `GET /api/health`, and `GET /api/auth/me` with the existing session cookie. A missing sign-in is a finished check, not a failure. If a check fails, the splash stops on that line and a button opens the wheel or the page anyway. The phone keeps the splash up for about 2.5 seconds when every check passes, so the lines can be read, and no longer than the timeouts on the requests. The website shows the splash once per browser tab session, skips the extra wait when the system asks for reduced motion, and leaves the page in the document underneath. The live site was not deployed. This branch's health response says `0.6.0-preview`; production still answers `0.5.0` until someone deploys it.

### Changed

- Android `versionCode` is 11, `versionName` is 0.6.0-preview. The v0.5.2 paddle notches, the separate gear buttons, and the editor arrows are unchanged. Width, height, and the move arrows in the edit tab work the same way as before.
- The rotating knob is drawn on every wheel you can drive, not only the MOZA KS demo. Locked wheels stay locked.

## [0.5.2] - 2026-11-22

Test release. v0.5.0 stays the stable download on the website and at `releases/latest`. This build is a GitHub prerelease for testing.

### Fixed

- Tapping shift up lights only the up zone, and tapping shift down lights only the down zone. The other buttons on the wheel stay as they were. One tap sends one gear-up or one gear-down, not a press of every button.
- Where a paddle zone crosses a button, the overlay now shows a notch cut out of the zone. If the button sits on the edge, that notch is a half circle. A tap inside the notch, including the small margin around the button, presses that button. A tap on the rest of the zone still shifts.
- Assetto Corsa's control screen can see gear up and gear down as two different joystick buttons. The receiver types Page Up for gear up and Page Down for gear down as well.
- On vJoy, gear up is button 31 and gear down is button 32 when the device has at least 32 buttons. Open vJoyConf, set the device to 8 axes and 32 buttons, apply, and start the receiver again. If the device has fewer buttons, the console prints the two button numbers it is using instead. Those can overlap wheel buttons until the count is 32. Each gear button is set once per update, so the press stays down long enough for the game to notice it.
- The keyboard keys for the paddles (and for wheel buttons past the Xbox pad) were never delivered, because the key packet was the wrong size. Page Up and Page Down now arrive while the zone is held.
- When the wheel itself is the Xbox pad, that pad has no button 31. If vJoy is installed, the receiver also opens it and moves only the two paddle buttons there, with the axes held in the center so the car is not steered by that second device.

### Added

- A Telegram link for chat, update news, polls, and downloads: https://t.me/racingwheelpro. On the phone it is Setup, then Profile, then Join our Telegram. The Windows receiver prints it at startup and shows Join our Telegram on its local page (http://127.0.0.1:41513/). The website footer and the download section use the same link. The address is the `telegram` value in `community.json` at the root of the repo. The site source includes the link. The live site is not updated by this release and still serves v0.5.0.

### Changed

- Android `versionCode` is 8, `versionName` is 0.5.2.
- v0.5.1 is marked as a prerelease so the latest stable release remains v0.5.0.

## [0.5.1] - 2026-11-21

Paddle zones can be resized, and they no longer cover a button.

### Added

- The edit tab resizes each shifter on its own. Width, height, and vertical position have sliders, and the zone itself has drag handles: the inner edge sets the width, the top and bottom edges set the height, and the middle slides the band. Reset paddles restores that wheel's default. The sizes are stored in the profile. Width is clamped to 8–45% of the wheel and height to 16–65%.
- Default zones are 20% of the wheel width and 32% of its height, placed on the grip and the paddle. Each template carries its own vertical center when the art sits higher or lower, plus the wheel plate bounds so the band starts at the wheel rather than the transparent margin.

### Changed

- A button or knob circle, plus a small margin, is cut out of any shifter rectangle it crosses. The overlay draws the notch, including a half-circle when the control sits on the edge. A touch in the notch presses the button. The cut-out is recomputed as the zone or the hotspot moves, and it stays aligned while the wheel turns.
- Android `versionCode` is 7, `versionName` is 0.5.1.

## [0.5.0] - 2026-11-21

Paddle shifters on every wheel, and pedals that can sit in from the screen edge.

### Added

- Gearshift zones on every wheel template. The left zone is shift up and the right zone is shift down. Each rectangle is 27% of the wheel width, measured in from that edge, and 47% of the wheel height, centered on the paddles. The fractions live in `ShifterZones`. Touches in a zone shift instead of pressing a button whose center sits there, and a held paddle keeps shifting while the wheel turns. A faint overlay draws the rectangles and can be hidden in Setup → Buttons.
- The state packet uses the previously reserved byte at offset 32 as a gear field (bit 0 = shift up, bit 1 = shift down). Older receivers ignore it. The Windows receiver types Page Up and Page Down, and holds vJoy buttons 31 and 32. Each paddle can also be mapped to a virtual button 1–32.
- Pedal edge inset. Throttle and brake each have a slider, 0–10% of screen width, for how far that pedal sits in from the bezel. The value is stored in the profile, applied on the drive screen, and reset with Reset edges. A small preview on the pedals page moves with the slider.

### Changed

- Android `versionCode` is 6, `versionName` is 0.5.0.

## [0.4.0] - 2026-11-18

AI driving coach for Assetto Corsa, on the free Cloudflare plan.

### Added

- The Windows receiver reads Assetto Corsa shared memory (`acpmf_physics`, `acpmf_graphics`, `acpmf_static`): speed, throttle, brake, steering, gear, rpm, lap position, lap times, car, track, and layout. When the game is not running it records the phone's steer, throttle, brake, and buttons instead.
- Laps are cut when `completedLaps` increases. Each corner gets a brake point, peak brake, trail-brake time, minimum speed, throttle pick-up, steering smoothness, and coasting time.
- Sessions are gzipped JSON and CSV in `%AppData%\RacingWheelPro\Logs`. The receiver's local page (http://127.0.0.1:41513/) lists them, shows a pairing code, and has an opt-in upload switch.
- The phone app has a Logs screen with throttle, brake, and steering traces, and a link to the account page.
- The website worker (`racingwheel-pro`) adds accounts (email, PBKDF2 password, HttpOnly cookie), device pairing, sessions, corner metrics, and a leaderboard per track, layout, and car. Pages: `/account`, `/session/:id`, `/leaderboard`. Charts use Chart.js. A log can also be uploaded by hand, including a Sim Racing Telemetry CSV.
- Workers AI (`@cf/openai/gpt-oss-120b`) writes per-session notes from the corner metrics, not from the raw trace. Reports are cached in D1. Free accounts get 5 analyses a day; a Premium flag on the user raises that to 40.
- Official Assetto Corsa track folders and layouts, including Nordschleife, Barcelona GP and MotoGP, Brands Hatch GP and Indy, and Red Bull Ring. Car folders from the base game, plus any custom name.

### Fixed

- The receiver's pace readout counted packets inside a ~100 ms refresh and printed that count as Hz, so a healthy stream read as 6–22 Hz. It now measures a full second. UDP is read on its own thread, and the virtual controller is submitted every 10 ms from the latest sample, so the pad updates at 100 Hz.
- The phone no longer looks up the PC address on every packet, and it no longer copies every state packet onto TCP. That flush was holding the 100 Hz sender back. UDP remains the telemetry path. TCP is the hello and the ping.

### Changed

- Android `versionCode` is 5, `versionName` is 0.4.0.

## [0.3.0] - 2026-11-17

Free garage, dial pointers, and a Play-ready build switch.

### Added

- The free build drives four wheels: MOZA KS, Thrustmaster SF1000, Mercedes-AMG F1, and Ascher McLaren. Every other template stays in the garage as a locked Premium tile with the action "Unlock Racing Wheel Premium - $3.99". Steering, pedals, buttons, the layout editor, and the PC receiver stay available without a purchase.
- Product flavors `free` and `premium`. The free flavor has no Play Billing types on its classpath. On this branch the premium flavor unlocks every wheel without a billing client, so the published app stays fully usable. The Play Billing client for the non-consumable product `racing_wheel_premium` is isolated on the unmerged `paid-version` branch. See [docs/BILLING.md](docs/BILLING.md).
- Rotary knobs take `startAngle` and `clockwise` in the template JSON. A pointer on the knob snaps to those detents when you tap + or − or drag around the dial, and each step is sent as the increment or decrement button. The free build draws the pointer on the KS wheel. Premium draws it on every wheel. Numbered dials on the KS, the SF1000 manettino, the Mercedes STRAT and HPP rings (16 marks), and the Ascher 1–12 scales are calibrated. Overlay renders are in `docs/knobs/`.
- Play listing kit in `play-store/`: title, descriptions, 1024×500 feature graphic, 512×512 icon, phone screenshots, data-safety answers, content-rating notes, and `PUBLISHING.md`.
- Store build switch `-Pracingwheel.genericNames=true` (or `RWP_GENERIC_NAMES=true`). UI text uses "F1 Wheel A" and "GT3 Wheel B" instead of manufacturer names. The photographs are not deleted. `PUBLISHING.md` explains why Play's IP rules still make those photographs a rejection risk.
- Upload-key signing from `RWP_UPLOAD_STORE_FILE` and the matching password variables, in the environment or `gradle.properties`. The keystore is not committed. Release builds that have no upload key still sign with the public development keystore.
- Website section "Free vs Premium" at $3.99 once, and a privacy policy at `/privacy` plus `website/PRIVACY.md`.
- Android `applicationId` `site.myracingwheel.app`, `targetSdk` 35, `versionCode` 4, and R8 on release. Bundles are `bundleFreeRelease` and `bundlePremiumRelease`.

### Changed

- `versionName` is 0.3.0.

## [0.2.1] - 2026-11-17

Transparent wheel plates.

### Changed

- Every wheel template PNG is a transparent cutout. Studio backdrops, floor shadows, reflections, and corner gallery badges are gone. The canvas size and the wheel's position are unchanged, so the existing normalized hotspots still line up.
- The manufacturer gallery draws those cutouts on the same carbon ground as the app, with a soft highlight so dark rims stay visible.
- Android `versionCode` is 3.

## [0.2.0] - 2026-11-16

Manufacturer garage and thirty car-wheel templates.

### Added

- Optional `manufacturer` field on `racing-wheel-pro.template/v1`. Older files still load. A missing field is treated as MOZA when the maker is MOZA, and otherwise left blank.
- Garage brand tabs: Porsche, McLaren, Audi, BMW, Ferrari, Mercedes, Alfa Romeo, KTM, then MOZA. A brand with no templates shows Coming soon.
- Nine Porsche templates, eight McLaren, seven Ferrari (including three road wheels), and six Mercedes (including two road wheels). Each control is a measured hotspot. A button uses one slot and a rotary uses two, and every template stays between 4 and 18 slots.
- Website section “Wheels by manufacturer”, with the same tabs, a gallery, and a trademark line. Canonical and Open Graph URLs are `https://myracingwheel.site/`.
- `tools/build_manufacturer_wheels.py` writes the car-wheel JSON, copies the PNGs, and draws alignment overlays in `docs/align/`.

## [0.1.0] - 2026-11-12

First working release: an Android wheel and a Windows receiver.

### Added

- Android app (Kotlin, Jetpack Compose), landscape and full screen, package `com.racingwheelpro`.
- Signed release APK and a debug APK. `./gradlew assembleDebug` and `./gradlew assembleRelease`.
- Four MOZA wheel templates, each a PNG plus `racing-wheel-pro.template/v1` JSON: FSR (screen), KS (GT, three knobs), FSR2 (dash), and GS (five knobs). Hotspots are measured against the source art.
- Layout editor: drag, nudge, resize, add a button or rotary, delete, save on the device, copy JSON, and reset to the built-in layout. Drive screen can outline the hotspots.
- Steering from the game-rotation vector, the rotation vector, or the accelerometer, with unwrap past 180°. Touch-drag steering when no sensor is available.
- Steering setup: lock-to-lock range (40–1080°), sensitivity, dead zone, linearity, smoothing, on-screen rotation cap, gravity scale, sensor choice, zero reference, invert, haptics, auto-center, and fastest sensor rate.
- Separate throttle and brake zones, plus an optional clutch strip. Position mode and relative (millimetre) mode. Per-axis gamma, dead zone, outer clamp, invert, instant or fade release, and an exclusive-axis option.
- Button map for outputs 1–32. Modes: momentary, toggle, and long-press. Rotaries emit an increment button, a decrement button, and a knob position. Volume keys can be bound.
- Profiles saved per wheel and per game.
- Wi-Fi discovery on UDP 41510, telemetry on UDP 41511 at about 100 Hz, and a TCP session on 41512 for hello and latency. Manual IP entry. USB path via `adb reverse` to 127.0.0.1.
- RWP1 binary protocol: separate steer, throttle, brake, and clutch, a 32-bit button mask, and eight knob bytes.
- Windows receiver, `RacingWheelPro-PC.exe`, self-contained .NET 8, win-x64, single file. ViGEm Xbox 360 output, vJoy output (32 buttons, 8 axes), keyboard keys for buttons 15–32, and a console dashboard when no driver is present. `--simulate`, `--json`, and `--self-test`.
- ViGEm rumble forwarded to the phone as a short vibration.
- JVM unit tests for the packet codec, inverse rotation of hotspots, pedal curves, and steering curves.
- Template generator (`tools/make_assets.py`) and UI preview renderer (`tools/render_previews.py`).
- GPL-3.0 license, with the additional permissions from Touch Racer's `LICENSE_EXCEPTIONS` reproduced in this repository. Credit to Touch Racer by Vasiliy Shirkin (thelv).

[Unreleased]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/compare/v0.6.3...HEAD
[0.6.3]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/compare/v0.6.2...v0.6.3
[0.6.2]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/compare/v0.6.2-preview...v0.6.2
[0.6.2-preview]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/compare/v0.6.0...v0.6.2-preview
[0.6.0]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/compare/v0.6.0-preview...v0.6.0
[0.6.0-preview]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/compare/v0.5.2...v0.6.0-preview
[0.5.2]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/compare/v0.5.1...v0.5.2
[0.5.1]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/compare/v0.2.1...v0.3.0
[0.2.1]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/releases/tag/v0.1.0
