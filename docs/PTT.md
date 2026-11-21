# Push-to-talk

A walkie-talkie on the steering wheel. Hold the **PTT** ring to talk to other drivers in the same room. Release it to stop. The phone shows **TRANSMITTING** while you hold it, and a separate **RX** pill while somebody else has the floor.

This is a module. The 36-byte driving packet is unchanged. The app builds with the module compiled in, and it builds again with the module left out.

Nothing here is deployed. The relay stays dark until the steps in [Deploy](#deploy) are run by hand.

## Why this shape

Cloudflare RealtimeKit's public plan page does not offer it on the free plan (the docs mention an included minute allotment, then a per-minute price). A WebRTC SFU would also pull a native library into the APK and an API token into the phone. That is the wrong fit for a free, self-contained preview.

Workers Free does include SQLite-backed Durable Objects. One object per room accepts hibernating WebSockets and forwards 20 ms Opus packets to the other drivers. The object never writes a row. Frames are not logged and not stored. When the socket closes, the driver is gone.

The phone does not share the website's sign-in cookie, so the room is anonymous: a display name plus a room code. If the name is still the default, the receiver fills it from the driver name in Assetto Corsa.

### How the room is chosen

Assetto Corsa's shared memory (`acpmf_static` and the rest) has the track, the car, and the player name. It does not have the server address. The launcher writes that to:

```text
Documents\Assetto Corsa\cfg\race.ini
```

under `[REMOTE]` (`ACTIVE`, `SERVER_IP`, `SERVER_PORT`, `SERVER_NAME`, `NAME`) before the session starts. The Windows receiver reads that file and the live player name, and sends them to the phone on TCP frame type **6** about every two seconds. The phone turns the server name into the room code. A short name falls back to `IP-<address>-<port>`. Single player (`ACTIVE=0`) clears the room.

The same slug is in three places, with the same unit tests:

- `voice/src/main/java/com/racingwheelpro/voice/VoiceProtocol.kt`
- `worker/src/voice_logic.ts`
- `pc/AcRaceIni.cs`

`Official Monza #1` is room `OFFICIAL-MONZA-1` on every side. Type that code, or any other 4–24 character code, to meet drivers who are not on that server.

**Follow PC** is on by default. When the receiver reports a new server, the phone joins it. Turn it off to stay in a code you typed.

## On the wheel

The radio is a hotspot of kind `ptt`, drawn on the wheel picture (bottom center until you move it). It rotates with the wheel, the paddle zones cut a hole around it, and it does not press a gamepad button.

- **Edit** tab: drag it, nudge it, resize it, **SAVE** the layout. **ADD RADIO** puts it back if you deleted it. The same tab has the room, your name, the relay address, **JOIN**, **SYNC PC**, follow, listen volume, and mute.
- **Drive** screen: hold the ring to talk. **TRANSMITTING** is the red pill. **RX** plus the other driver's name is the cyan pill. **SYNC** joins the server the PC last reported. **MUTE** silences playback. Your own voice is not played back.

The first hold asks for the microphone. After that the capture stays open while you are in the room, and frames are sent only while the ring is held. One driver has the floor. A second hold gets "has the radio" and does not transmit.

Playback is 16 kHz mono Opus, 20 ms frames, 16 kbit/s, through the loudspeaker. Listen volume is an in-app gain. Mute sends nothing to the speaker.

The default relay is `wss://myracingwheel.site/api/voice/ws`. It answers only after the deploy below. The relay field in the editor can point at another `wss://` host. The module does not record audio on the phone either: the only file it writes is `voice-prefs.txt` (room, name, volume, mute, follow, and wheels where you removed the button).

## Free-tier limits

Checked against the Cloudflare docs (Durable Objects pricing, November 2026). Workers Free, SQLite-backed Durable Objects:

| Meter | Included | What this relay does |
| --- | --- | --- |
| DO requests | 100,000 / day | One join is one request. Incoming WebSocket messages bill 20:1, so 20 audio frames count as one request. Outgoing copies are free. |
| DO duration | 13,000 GB-s / day | Hibernation bills only while a message is being handled. An idle room does not keep the 128 MB allocation awake. |
| Workers requests | 100,000 / day | The upgrade request only. Audio does not re-enter the Worker. |
| CPU | 10 ms per message on the free plan | A forward of a ~40 byte packet is well under that. |
| SQLite rows | 5 million reads, 100,000 writes / day | Unused. The class is SQLite-backed because that is what the free plan allows, and it never calls storage. |

At 50 frames per second (one 20 ms frame) the request budget is about **11 hours of somebody talking per UTC day**, for the whole account, not per room. Listeners do not multiply it: eight drivers in a room still count as one incoming stream while a single person holds the floor. A quiet lobby costs a handful of requests to join and then hibernates.

Per room, in the object, before any forward:

- 8 drivers
- 12 new joins in a warm minute (the counter lives in memory and resets if the object hibernates)
- 55 frames in a rolling second (50 is the normal rate; the extra five cover a timer bunching up)
- 200 bytes of Opus per frame
- one transmitter

Over the cap, the frame is dropped. The sender is not disconnected. Past the daily request or duration cap, Cloudflare rejects further operations until 00:00 UTC. There is no bill on the free plan; the call fails instead.

That 11 hours is enough for a friends-and-drivers preview. It is not enough for a public always-on channel. A paid Workers plan, or Cloudflare Realtime (1,000 GB of SFU egress included, then $0.05/GB), is the step up if the room is busy all day. This branch does not turn either of those on.

## Build

`gradle.properties` sets `racingwheel.voice=true`. The preview APK is the free debug build with that flag on (`versionName` `0.5.1-ptt`).

```bash
# Radio included. Output: app/build/outputs/apk/free/debug/app-free-debug.apk
./gradlew :app:assembleFreeDebug :voice:test :app:testFreeDebugUnitTest

# Radio compiled out. No :voice dependency, no RECORD_AUDIO permission.
./gradlew :app:assembleFreeDebug :app:testFreeDebugUnitTest -Pracingwheel.voice=false
```

`-P` has to match for both `settings.gradle.kts` (whether `:voice` is included) and the app module (which source set and dependency it uses). The disabled build compiles `app/src/novoice` instead of `app/src/voice`. `VoiceBridge` in the main source set is the only type the driving screen calls.

Worker tests, from `worker/`:

```bash
npm ci
npm test
```

Receiver tests, from `pc/`:

```bash
dotnet test RacingWheelPro.PC.Tests
```

## Merge later

The branch is `feature/ptt-voice`. It is not merged. Leave `main`, `tablet-version`, and `paid-version` alone until you want this.

1. Merge `feature/ptt-voice` into the branch that should grow the radio. Do not merge it into `tablet-version` or `paid-version` unless that branch should get the module too.
2. Leave `racingwheel.voice=true` in `gradle.properties` if the next APK should talk. Set it to `false` if you want the driving app unchanged while the worker files sit in the tree. With `false`, `:voice` is not on the compile classpath.
3. Rebuild the Windows receiver. An older `RacingWheelPro-PC.exe` still drives the car. It just never sends TCP type 6, so **SYNC** reports that no server has arrived and you type the room yourself.
4. Deploy the worker with the steps below before expecting `wss://myracingwheel.site/api/voice/ws` to answer. The phone shows "Relay is not up yet" until then.
5. On a phone that already has a wheel layout saved, open **Edit**, confirm the **PTT** ring, move it if you want, and tap **SAVE**. A fresh install places the ring at the bottom center of the wheel.

Files the driving screen actually calls:

- `app/src/main/java/com/racingwheelpro/voice/VoiceBridge.kt` — the flag-shaped API
- `app/src/voice/…` and `app/src/novoice/…` — the two implementations
- `app/src/voice/AndroidManifest.xml` — `RECORD_AUDIO`, merged only when the flag is on
- `RaceSession` routes a `ptt` hotspot to `voice.onPtt` and does not put it on the button mask
- `LinkClient` delivers TCP type 6 to `voice.onServerFrame`
- `:voice` — protocol, Opus, socket, microphone
- `worker/src/voice_logic.ts`, `worker/src/voice_room.ts`, the `VOICE_ROOMS` binding
- `pc/AcRaceIni.cs` — the `race.ini` reader

There is no D1 migration. A roster table would outlive the conversation, and the room already forgets a driver when the socket closes.

## Deploy

Run this on your machine after `npx wrangler login`. Do not run it from CI, and do not run it just to try the branch.

From `website/` if the site bundle is stale, then from `worker/`:

```bash
cd website && npm ci && npm run build && cd ../worker
npx wrangler d1 execute racingwheel-pro --remote --file=migrations/0001_init.sql
npx wrangler deploy
```

`wrangler.toml` adds one Durable Object binding:

```toml
[[durable_objects.bindings]]
name = "VOICE_ROOMS"
class_name = "VoiceRoom"

[[migrations]]
tag = "v1-voice"
new_sqlite_classes = ["VoiceRoom"]
```

`new_sqlite_classes` is required on the free plan. The class does not create tables. If a later deploy says the tag `v1-voice` is already applied, leave the migration block as it is and deploy the script only.

Check, still without sending audio:

```bash
curl -s https://myracingwheel.site/api/voice
```

Expect `"codec":"opus"` and `"storedAudio":false`. `/api/health` stays the coach health check.

Then install the preview APK, open **Edit**, tap **JOIN** (or **SYNC** with the new receiver and Assetto Corsa in a multiplayer server), and hold **PTT** on a second phone in the same room.

Local wrangler (`npx wrangler dev --local`) can host the same object. Point the relay field at that machine only if the phone can open it as `wss://`. The app does not allow cleartext `ws://`.

## What it will not do

- It will not record, upload, or replay audio.
- It will not sign the driver in with the website account. A later change can pass a device token; the room code is what puts a grid in one channel today.
- It will not read the server from shared memory, because that address is not in the shared pages.
- It will not keep working for a public channel once the free request budget is spent for the day. The caps above are the whole capacity.
