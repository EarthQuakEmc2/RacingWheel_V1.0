# Data safety form

Answer from the Play Console Data safety questionnaire. This matches the app in this repository and the privacy policy at https://myracingwheel.site/privacy.

## Overview

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | No |
| Is all of the user data collected by your app encrypted in transit? | Not applicable. The app does not send user data to a developer server. |
| Do you provide a way for users to request that their data be deleted? | Not applicable. The developer does not hold an account or a copy of their data. |

"No" is correct because the phone never uploads telemetry, contacts, location, or identifiers to us. Google Play Billing, on the premium build only, is processed by Google. We do not receive payment details. Declare that under the billing questions if the console asks about purchases, and do not list payment info as data the developer collects.

## Data types to leave unchecked

- Location, including approximate and precise
- Personal info (name, email, address, phone, race, political)
- Financial info (the developer does not receive it; Play does)
- Health and fitness
- Messages
- Photos or videos (wheel art is shipped in the APK, not collected)
- Audio
- Files and docs
- Calendar
- Contacts
- App activity, including in-app search and installed apps
- Web browsing
- App info and performance (no crash SDK, no diagnostics SDK)
- Device or other IDs (no advertising id, no account id)

## What the app does use the network for

The phone sends a steering packet to a PC the user chooses, on the local network or through `adb reverse` to `127.0.0.1`. That packet contains steering, pedals, button bits, and knob positions. It does not contain a name the developer can see, and it does not leave the user's LAN unless the user points it at a machine they control.

`ACCESS_WIFI_STATE` and multicast are for discovering that PC. The result stays on the phone.

The premium build's Play Billing client talks to Google Play to query and acknowledge `racing_wheel_premium`. That traffic is Google's, under Google's terms.

## Security practices

- Data is not sold.
- Data is not shared with third parties for advertising.
- There is no account to delete.
- The entitlement cache is a boolean in app-private preferences. Uninstalling the app deletes it. Play remains the source of truth and restores the purchase.
