# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Nothing yet.

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

[Unreleased]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/compare/v0.2.1...v0.3.0
[0.2.1]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/releases/tag/v0.1.0
