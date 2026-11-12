# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Nothing yet.

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

[Unreleased]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/EarthQuakEmc2/RacingWheel_V1.0/releases/tag/v0.1.0
