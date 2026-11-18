# Billing and the free / premium split

Racing Wheel Pro is GPL-3.0 with the Touch Racer additional permissions in `LICENSE_EXCEPTIONS`. Those permissions allow monetization code to stay unpublished, and they require the published application to stay fully usable without that code. This is how the project keeps both.

## Flavors

The Android module has one flavor dimension, `tier`:

| Flavor | Classpath | What the driver gets |
| --- | --- | --- |
| `free` | No Play Billing types. `FreePremiumGate` in `app/src/free`. | Four wheels (see below). Steering, throttle, brake, buttons, the layout editor, and the PC receiver all work. One wheel draws the animated knob pointer as a demo. |
| `premium` | On `main`, `OpenPremiumGate` in `app/src/premium`. No Play Billing dependency. | Every wheel and every knob pointer. This is the fully functional open build. |

```bash
./gradlew :app:assembleFreeDebug
./gradlew :app:assemblePremiumDebug
./gradlew :app:bundleFreeRelease
./gradlew :app:bundlePremiumRelease
```

`applicationId` is `site.myracingwheel.app` for both flavors, so they replace each other on a phone. `versionName` is `0.3.0`.

The free wheels are `moza_ks`, `ferrari_sf1000`, `mercedes_amg_f1`, and `mclaren_ascher_dark`. They are the plates whose numbered dials sit on the hotspot centers. Every other template remains in the app and shows up in the garage as a locked Premium tile. The garage always offers **Unlock Racing Wheel Premium - $3.99**.

## Why the Play client is not on main

Google Play Billing is the monetization code `LICENSE_EXCEPTIONS` lets us keep off the published tree. The branch `paid-version` replaces `app/src/premium` with a Billing Library 7 client and adds `premiumImplementation` of `com.android.billingclient:billing-ktx`. The free flavor does not compile that client. The branch is not merged.

That client sells one non-consumable product:

| Field | Value |
| --- | --- |
| Product id | `racing_wheel_premium` |
| Type | One-time, non-consumable |
| Price | $3.99 USD, set in Play Console, not in the APK |
| Grant | `queryPurchasesAsync` on startup, then acknowledge if needed |
| Cache | `SharedPreferences` key `premium_owned`, used only when the query has not succeeded |

`EntitlementPolicy` in the published source is the decision the Play client must use:

- A successful query wins. Owned means unlocked. Not owned means locked, and the cache is updated.
- A failed or unfinished query keeps the cache, so an offline restart does not take a wheel away.
- Debug premium builds grant the entitlement so the test APK can be driven without a Play Console product. Release builds do not.

The open `premium` flavor on `main` does not use that cache. It unlocks every wheel in code, with no network call to Google.

## Store names

Play's intellectual-property rules are a separate problem from billing. `-Pracingwheel.genericNames=true` (or `RWP_GENERIC_NAMES=true`) builds the same flavors with manufacturer names removed from the UI. The photographs stay in the APK. Read `play-store/PUBLISHING.md` before uploading anything.
