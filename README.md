# Racing Wheel Pro

An Android app that turns a phone into a racing wheel for PC sim games, and a Windows receiver that exposes that input as a virtual game controller.

Landscape, full screen. You hold the phone like a wheel, roll it to steer, and use the side of the screen for throttle and brake. Buttons and rotary knobs are hotspots on a wheel picture, so they stay under your thumbs when the picture rotates.

## What this version does

- Four MOZA wheel templates, each a photo plus a JSON layout of every button and knob.
- A layout editor: drag hotspots, nudge, resize, add, delete, save on the phone, copy the JSON, or reset to the built-in layout. The drive screen can also draw the hotspot rings.
- Steering from the game-rotation sensor, the accelerometer, or a finger drag when no sensor is present. Range (40–1080°), sensitivity, dead zone, linearity, smoothing, invert, center, gravity scale, and a haptic tick at full lock.
- Separate throttle and brake. Position (finger height in the zone) or relative (millimetres of drag, the Touch Racer idea). Per-pedal gamma, dead zone, outer clamp, invert, instant or fade release, exclusive axes, and an optional clutch strip.
- Every hotspot maps to a virtual button 1–32. Modes are momentary, toggle, and long-press. Rotaries pulse a decrement/increment pair and also send a position.
- Profiles store the map and the setup per wheel and per game. Volume keys can be bound.
- The phone finds the PC on Wi-Fi, or you type an address, or you use `adb reverse` over USB.
- The PC receiver feeds a ViGEm Xbox 360 pad when [ViGEmBus](https://github.com/nefarius/ViGEmBus/releases) is installed, or a vJoy device when [vJoy](https://github.com/jshafer817/vJoy/releases) is installed. Buttons past the Xbox pad's 14 become keyboard keys. Without either driver it still prints a live dashboard. Rumble from the virtual pad is sent back to the phone as a short vibration.

## What is not in this build

- Bluetooth. Touch Racer speaks Bluetooth as well as Wi-Fi. This build is Wi-Fi plus `adb reverse`.
- Real force feedback. Only the ViGEm rumble motors are forwarded as a haptic pulse.
- Game telemetry. The shift-light bar follows throttle, not the car's RPM.
- A per-button keyboard picker on the phone. The phone sends button numbers. The PC maps 1–14 to the Xbox pad and 15–32 to F1–F12, then digit keys, then letters.
- An iOS app, and a Linux uinput build of the receiver. The receiver is a Windows exe. On Linux it runs as a console monitor, which is how the protocol was tested here.

The Windows exe was cross-compiled in this environment. It was not launched under Windows, and ViGEmBus is not installed here, so the virtual-pad path is compiled and guarded, not exercised against a game. Steering, pedals, and buttons were checked with a UDP/TCP smoke test against the same parser the exe contains. The Android unit tests cover the packet codec, the rotation math, and the curves.

## Install the phone app

The signed release APK is `RacingWheelPro.apk` (also built as `app/build/outputs/apk/release/app-release.apk`).

On the phone, allow install from unknown sources and open the APK. Or from a computer with the phone in USB debugging:

```bash
adb install -r RacingWheelPro.apk
```

The release build is signed with the development key in `app/release.keystore` (alias `racingwheelpro`, store password `racingwheelpro`). That key is for reproducible builds, not for a Play Store listing. Replace it before you ship.

Build it yourself (JDK 17 or 21, Android SDK 34):

```bash
export ANDROID_HOME=/opt/android-sdk   # or your SDK path
./gradlew assembleDebug                # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease              # signed release APK
./gradlew test                         # packet, geometry, and curve tests
```

`local.properties` can set `sdk.dir` if you do not export `ANDROID_HOME`.

## Run the PC receiver

The ready-to-run file is `RacingWheelPro-PC.exe`. It is a self-contained .NET 8 single-file binary for 64-bit Windows. No separate runtime install.

1. Install a virtual controller driver if you want games to see a device. The exe still runs and logs input without one.
   - **ViGEmBus** (recommended). Games see an Xbox 360 pad. Download the latest release from [github.com/nefarius/ViGEmBus/releases](https://github.com/nefarius/ViGEmBus/releases) and reboot if the installer asks. Project page: [github.com/nefarius/ViGEmBus](https://github.com/nefarius/ViGEmBus).
   - **vJoy**, if you want all 32 buttons and 8 axes on one device. Download from [github.com/jshafer817/vJoy/releases](https://github.com/jshafer817/vJoy/releases) (the maintained fork) or [sourceforge.net/projects/vjoystick](https://sourceforge.net/projects/vjoystick/). In vJoyConf, set device 1 to 8 axes and 32 buttons. `vJoyInterface.dll` must be on PATH or next to the exe (the installer puts it in System32).
2. Put the phone and the PC on the same Wi-Fi. Run `RacingWheelPro-PC.exe`. The phone discovers it and connects. The window shows steer, throttle, brake, clutch, buttons, and packet rate.
3. In the game, bind an Xbox pad (ViGEm) or the vJoy device.

```text
RacingWheelPro-PC.exe
RacingWheelPro-PC.exe --simulate          # sweep the axes with no phone
RacingWheelPro-PC.exe --output vigem      # Xbox pad only
RacingWheelPro-PC.exe --output vjoy       # vJoy device 1, all 32 buttons
RacingWheelPro-PC.exe --output monitor    # log only
RacingWheelPro-PC.exe --no-keyboard       # do not inject keys for extra buttons
RacingWheelPro-PC.exe --json              # one JSON object per packet on stdout
RacingWheelPro-PC.exe --self-test
```

USB instead of Wi-Fi, with the phone plugged in and USB debugging on:

```bash
adb reverse tcp:41512 tcp:41512
adb reverse tcp:41511 tcp:41511
adb reverse tcp:41510 tcp:41510
```

On the phone, open Link and tap **USB / ADB**. That connects to `127.0.0.1`, which `adb reverse` forwards to the PC.

Rebuild the exe from this repo (the .NET 8 SDK):

```bash
cd pc
dotnet run -c Release -- --self-test
dotnet publish -c Release -r win-x64 --self-contained true \
  -p:PublishSingleFile=true \
  -p:IncludeNativeLibrariesForSelfExtract=true \
  -p:EnableCompressionInSingleFile=true \
  -o publish
```

`publish/RacingWheelPro-PC.exe` is the file you copy to Windows.

### What the virtual pad receives

| Phone | ViGEm Xbox 360 | vJoy device |
| --- | --- | --- |
| Steering −1..1 | Left stick X | Axis X |
| Throttle 0..1 | Right trigger | Axis Y |
| Brake 0..1 | Left trigger | Axis Z |
| Clutch 0..1 | Right stick Y, if the clutch is in use | Axis Rx |
| Knob slots 0 and 1 | Right stick X / Y | Ry, Rz |
| Knob slots 2 and 3 | — | Slider, Dial |
| Buttons 1–14 | A B X Y LB RB Back Start LS RS, d-pad up down left right | Buttons 1–14 |
| Buttons 15–32 | Keyboard F1–F12, then 1–0, then A… | Buttons 15–32 |

Button 1 is bit 0 of the mask.

## Architecture

```text
phone (Kotlin)                         PC (C# / Windows)
┌─────────────────────┐                ┌──────────────────────────┐
│ sensors + touch     │                │ UDP 41510  discovery     │
│ curves, profiles    │  RWP1 packets  │ UDP 41511  telemetry     │
│ wheel JSON hotspots │ ─────────────► │ TCP 41512  hello + ping  │
└─────────────────────┘                │ ViGEm / vJoy / keys      │
                                       └──────────────────────────┘
```

The phone sends the latest state about 100 times a second. TCP carries the same state plus a hello and a ping so the phone can show latency (half the round trip). UDP is the low-latency path and still works if TCP is blocked.

Holding the phone: landscape, screen facing you, upright is center. Roll it like a wheel. That is the same gesture Touch Racer uses (accelerometer Y, or pitch from the game-rotation vector, unwrapped when the lock-to-lock range is past 180°). Tap **CENTER** if your hands are not at the sensor's zero. If the phone has no rotation sensor, drag the wheel art sideways.

Touch is not Touch Racer's model. Touch Racer treats each half of the screen as a swipe pad: direction picks throttle, brake, or one of eight buttons. Here the wheel picture is the control surface. A finger on a drawn button hits that button, including after the picture has rotated, because the touch point is rotated back into the picture before the hit test. The left strip is the brake and the right strip is the throttle. Fingertip slop makes the small buttons easier to land without moving the drawn ring.

## Protocol (RWP1)

Little-endian. Version byte is `1`.

**Find**, phone → UDP port 41510, 24 bytes. Magic `RWPF`, version `u8`, reserved `u8`, reserved `u16`, name `16` bytes zero-padded.

**Host**, PC → the phone, and also broadcast on 41510. Magic `RWPH`, version `u8`, capability bits `u8` (1 ViGEm, 2 vJoy, 4 keyboard), TCP port `u16`, UDP port `u16`, name length `u8`, name bytes.

**State**, phone → UDP 41511 and, when TCP is up, as a TCP frame. 36 bytes:

| Offset | Type | Field |
| --- | --- | --- |
| 0 | char[4] | `RWPS` |
| 4 | u8 | version = 1 |
| 5 | u8 | flags, bit 0 = touch-steer |
| 6 | u16 | sequence |
| 8 | i16 | steer, −32767..32767 |
| 10 | u16 | throttle, 0..65535 |
| 12 | u16 | brake, 0..65535 |
| 14 | u16 | clutch, 0..65535 |
| 16 | u32 | buttons, bit 0 = button 1 |
| 20 | i8[8] | knob positions |
| 28 | u32 | client time, ms |
| 32 | u16 | reserved |
| 34 | u16 | reserved |

**Rumble**, PC → the UDP source of the last state. Magic `RWPR`, large motor `u8`, small motor `u8`.

**TCP frames.** `u8` type, `u16` payload length, payload.

| Type | Name | Payload |
| --- | --- | --- |
| 1 | HELLO | UTF-8 JSON: `name`, `wheel`, `profile`, `protocol` |
| 2 | STATE | the 36-byte state packet |
| 3 | PING | `u32` client time |
| 4 | PONG | `u32` echoed client time, `u32` server time |
| 5 | BYE | empty |

This is not the Touch Racer `ru.thelv.joy` packet. Same job, different bytes, because this app has separate pedals and more than eight buttons.

## Wheel template format

`schema` is `racing-wheel-pro.template/v1`. One JSON file and one PNG per wheel, both in `app/src/main/assets/wheels/`. Coordinates are fractions of the image. `r` is a fraction of the image width. The editor writes an override under the app's files directory; **RESET** deletes it and reloads the asset.

```json
{
  "schema": "racing-wheel-pro.template/v1",
  "id": "moza_fsr",
  "name": "MOZA FSR",
  "maker": "MOZA Racing",
  "style": "formula",
  "image": "moza_fsr_screen.png",
  "imageWidth": 1280,
  "imageHeight": 720,
  "blurb": "Formula wheel with a dashboard screen.",
  "hitSlop": 0.008,
  "controls": [
    {
      "id": "box",
      "label": "BOX",
      "kind": "button",
      "shape": "circle",
      "cx": 0.27891,
      "cy": 0.25139,
      "r": 0.01406,
      "color": "#F5C400",
      "defaultButton": 2
    },
    {
      "id": "map",
      "label": "MAP",
      "kind": "rotary",
      "shape": "circle",
      "cx": 0.38281,
      "cy": 0.59722,
      "r": 0.03281,
      "color": "#7CFF4A",
      "positions": 12,
      "knobSlot": 2,
      "decButton": 15,
      "incButton": 16
    }
  ]
}
```

`kind` is `button` or `rotary`. `shape` is `circle` (hit as a disc). `defaultButton`, `decButton`, and `incButton` are the suggested outputs; the profile can override them. `knobSlot` is 0–7 and is the byte sent in the state packet.

The four templates:

| Id | Picture | Face buttons | Rotaries |
| --- | --- | --- | --- |
| `moza_fsr` | FSR-style formula wheel with a screen | 10 | 7, including the two thumb encoders |
| `moza_ks` | KS / GT wheel | 11 | 3 (TURBO, TC, ABS) |
| `moza_fsr2` | FSR2-style formula wheel with a dash | 9 | 5 (MGU, MIX, ERS, TC, ABS) |
| `moza_gs` | GS-style wheel | 10 | 5 (MAP, TURBO, TC, ABS, DIFF) |

Hotspot centers were measured on the source renders (saturated-color peaks for the buttons, a pixel grid for the knobs). `tools/make_assets.py` is that measurement, and it also knocks the white studio background out of the PNGs. To add a wheel: drop a PNG in `assets/wheels/`, add a JSON file in the same shape, and rebuild. The editor can place the rings if you start from a copy. Planned additions are the wheels people actually drive in the sims: modern F1, GT3, and rally.

`tools/render_previews.py` draws the chrome and the rings from those JSON files. It is a preview, not an emulator capture.

## Touch Racer

The open-source project at [github.com/thelv/touchracer](https://github.com/thelv/touchracer) (Vasiliy Shirkin, [touchracer.com](https://touchracer.com/)) is the reference for this idea. Its published Android tree is GPL-3.0 with the extra permissions in that repo's `LICENSE_EXCEPTIONS` (the original author may use the code in a proprietary product, and unpublished art and monetization code can stay private).

Racing Wheel Pro does not copy that source. It is an independent Apache-2.0 program that follows the same approach where it worked: UDP discovery then a small binary state packet, tilt steering with a game-rotation sensor and an unwrap past 180°, pedal travel in millimetres as one of the modes, and a virtual game controller on the PC. The on-screen controls are the part that was redesigned.

## Roadmap

- More templates: current F1, GT3, LMP, and rally wheels, loaded the same way as the MOZA set.
- Bluetooth, for rooms where Wi-Fi is congested.
- Per-button keyboard choice stored in the profile and honored by the receiver.
- Game telemetry (SimHub or the game's UDP) so the shift lights and the wheel screen show RPM, gear, and flag color.
- A stronger force-feedback path than rumble-to-haptic.
- A Linux receiver using uinput, next to this Windows build.
