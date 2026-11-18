# Privacy policy

Racing Wheel Pro is an Android app and a Windows program that turn a phone into a steering controller for a PC you choose. This policy covers the Android app, the Windows receiver, and the website at https://myracingwheel.site/.

Last updated: 17 November 2026.

## Who we are

The project is published by its contributors at https://github.com/EarthQuakEmc2/RacingWheel_V1.0. There is no account system and no developer server that stores player data.

## What the app does with data

The phone reads its own motion sensors to compute a steering angle, and it reads touches for the pedals, buttons, and knobs. Those values are sent to the PC you connect, as a small binary packet, about 100 times a second. The packet contains steering, throttle, brake, clutch, a button mask, and knob positions. It does not contain your contacts, photos, location, or advertising id.

Discovery uses the local network. The phone broadcasts a short "find" packet and lists PCs that answer. You can also type an address or use a USB cable. Traffic stays on your network, or on the USB tunnel to your own computer. We do not receive a copy.

Settings and wheel-layout edits are stored on the phone, in the app's private files. Uninstalling the app deletes them.

## Premium purchase

The Play build of Racing Wheel Premium can offer a one-time in-app purchase, product id `racing_wheel_premium`, sold by Google Play. Google processes the payment. We do not receive your card number, and we do not run our own checkout. The app stores a local note that the purchase was seen, and it asks Play again the next time it starts. Google's privacy policy covers the payment.

The free build distributed from the project site has no billing client and does not contact Play.

## What we do not do

- We do not create an account.
- We do not show ads.
- We do not use analytics, crash-reporting SDKs, or advertising identifiers.
- We do not sell or share personal data.
- We do not collect precise location.

## Website

https://myracingwheel.site/ is a static site. It does not set its own analytics cookies. Pages are served as files. Your browser still talks to whatever host delivers the site, under that host's policy.

## Children

The app is a controller. It is not directed at children, and it does not knowingly collect personal information from anyone.

## Changes

When this policy changes, the date at the top changes with it, and the history is in the project repository.

## Contact

Open an issue on https://github.com/EarthQuakEmc2/RacingWheel_V1.0 or write to the address published on the Play Console listing. For a purchase refund, use Google Play's order history. We cannot see your payment.
