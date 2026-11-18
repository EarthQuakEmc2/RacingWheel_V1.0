# Publishing Racing Wheel Pro on Google Play

This is the checklist for a Play listing of `site.myracingwheel.app`. It is not a substitute for the current Play Console help pages. Policies move. Read them again on the day you submit.

The Android App Bundle you upload is **not** the APK on the GitHub release. The GitHub release is the free, open build. The Play bundle is the premium flavor from the unmerged `paid-version` branch, built with generic names. Details are below.

## Trademark and impersonation risk

Google Play's intellectual-property policy rejects and removes apps that use someone else's brand name, logo, or trade dress without authorization. That includes Ferrari, Porsche, Mercedes-Benz, Mercedes-AMG, McLaren, MOZA, Fanatec, Thrustmaster, Ascher, Cube Controls, and the wheel designs in the photographs. The risk is higher when the app is sold.

A listing titled with a car maker, a screenshot with a prancing horse, or an in-app string that says "Ferrari" is a common reason for removal, even when the developer adds "not affiliated" in the description. The disclaimer does not grant a license.

What this repository does about it:

- The store build switch strips those names from UI text. Build with `-Pracingwheel.genericNames=true` or `RWP_GENERIC_NAMES=true`. Wheel titles become "F1 Wheel A", "GT3 Wheel B", and so on. Garage tabs become Formula, GT, Cup, and Road. Profile names and the name sent to the PC are scrubbed the same way.
- The switch does **not** delete or rewrite the PNG files. The photographs still show badges, wordmarks, and car-maker shapes. Play review can reject the app for the images alone.
- Do not upload a production listing until the wheel art in that bundle is generic, or you have written permission from the rights holders. Replacement art that you want to keep proprietary must not be committed next to this code. `LICENSE_EXCEPTIONS` allows proprietary graphics only when they are not published with the source.
- The text in `play-store/listing/` does not use those brand names. Do not paste the README's wheel table into the store listing.
- The screenshots in `play-store/screenshots/` are rendered from the app chrome with generic titles. They still composite the current photographs. Treat them as layout proofs. Replace them before you submit if the art is still recognizable.

The GitHub project and https://myracingwheel.site/ are not the Play listing. They name the templates so contributors can find a file, and they carry a trademark notice. That notice is not permission to sell the same names on Play.

## Which binary goes to Play

| Build | Where it comes from | Upload it? |
| --- | --- | --- |
| `free` release APK | `main`, public development key | No. This is the GitHub download. It has no billing client. |
| `premium` on `main` | Open build, every wheel unlocked, no billing | No. It gives the paid set away and cannot take a purchase. |
| `premium` release AAB from `paid-version`, generic names | Play Billing client, product `racing_wheel_premium` | Yes. This is the store artifact. |

From a checkout of `paid-version`, with the upload key in the environment (see Signing):

```bash
./gradlew :app:bundlePremiumRelease -Pracingwheel.genericNames=true
```

The bundle is `app/build/outputs/bundle/premiumRelease/app-premium-release.aab`.

`targetSdk` is 35. `versionCode` must increase on every upload. `0.3.0` is `versionCode` 4. R8 minify and resource shrinking are on for release.

Confirm the merged manifest does not add `com.google.android.gms.permission.AD_ID`. This app does not use an advertising id.

## Signing

Enroll the app in Play App Signing. Google holds the app signing key. You hold a separate upload key.

Set these in the environment or in `~/.gradle/gradle.properties` (not in the repo):

```text
RWP_UPLOAD_STORE_FILE=/path/to/upload.jks
RWP_UPLOAD_STORE_PASSWORD=...
RWP_UPLOAD_KEY_ALIAS=...
RWP_UPLOAD_KEY_PASSWORD=...
```

If they are unset, the build falls back to `app/release.keystore`, whose password is published in this repo. That key is for reproducible GitHub APKs only. Do not enroll it as the Play upload key. Anyone who has the repo can sign with it.

Never commit a `.jks`, an upload keystore, or a password file. `.gitignore` ignores new keystores. The development keystore is already tracked on purpose and must stay the only one.

## Developer account

1. Create a Google Play Console developer account and pay the registration fee.
2. Finish identity verification. New personal accounts cannot ship to production until verification and the closed-testing requirement below are done.
3. Create the app. Package name: `site.myracingwheel.app`. Default language: English (United States). App name: Racing Wheel Pro. Free to install, with one in-app product.
4. Turn on Play App Signing and register the upload key certificate.

## In-app product

Create a one-time product, not a subscription.

| Field | Value |
| --- | --- |
| Product id | `racing_wheel_premium` |
| Name | Racing Wheel Premium |
| Description | Unlocks every wheel layout and the dial pointer on each knob. One time. |
| Type | Non-consumable |
| Price | $3.99 USD, and the local equivalents Play suggests |
| Status | Active |

The app reads the price from Play when the client is present and falls back to the label `$3.99`. Do not hard-code a different price in the console.

The client queries purchases on startup with `queryPurchasesAsync`, acknowledges a completed purchase, and caches the result. License testers in the console can buy without being charged. Test acknowledge and restore on a second install before you open a production track.

## Closed testing for new personal accounts

Personal developer accounts created after the current Play policy change have to run a closed test before they can apply for production. The usual requirement is:

- At least **12 testers** opted in to the closed test.
- The test stays open for **14 consecutive days**.
- You then apply for production access from the console, and a reviewer looks at the test.

Confirm the number and the duration in Play Console on the day you start. They have changed before. Internal testing (up to 100 testers, fast installs) does **not** by itself satisfy the 14-day closed test. Use internal testing first to check the bundle, then start the closed test and do not pause it.

Organization accounts have a different path. Read the account-type page that matches the account you actually created.

## Listing assets in this folder

| File | Play requirement |
| --- | --- |
| `listing/title.txt` | App name, 30 characters or fewer |
| `listing/short-description.txt` | 80 characters or fewer |
| `listing/full-description.txt` | Full description, no brand names |
| `feature-graphic.png` | 1024×500 |
| `icon-512.png` | 512×512, 32-bit PNG |
| `screenshots/phone-*.png` | Phone screenshots, rendered from the app chrome |
| `data-safety.md` | Answers for the Data safety form |
| `content-rating.md` | Notes for the IARC questionnaire |
| `../website/PRIVACY.md` | Source for the privacy policy |
| Privacy URL | https://myracingwheel.site/privacy |

Also required in the console, and not generated here: a feature graphic you are willing to stand behind after the art review, a hi-res icon, at least two phone screenshots that do not infringe, the privacy policy URL, a content-rating questionnaire, target audience and ads declaration (this app has no ads), news-app declaration (no), and government-app declaration (no).

## Release order

1. Internal testing. Upload the generic-name premium AAB. Install from the internal opt-in link. Confirm the free wheels drive, locked tiles show the $3.99 action, a license-test purchase unlocks the rest, a reinstall restores it, and the UI text contains none of the brand names in the trademark section.
2. Closed testing. Recruit at least 12 testers who will stay opted in for 14 days. Leave the release active.
3. Apply for production access once the console says the closed test qualifies.
4. Production. Roll out in stages. Keep the generic-name switch on.

## Before you press submit

- Listing text has no Ferrari, Porsche, Mercedes, McLaren, MOZA, Fanatec, Thrustmaster, Ascher, or Cube Controls.
- Screenshots and the feature graphic don't either, including pixels.
- `racing_wheel_premium` is active at $3.99 and is a non-consumable.
- The uploaded bundle is the paid-version premium release, generic names, upload key, not the development keystore.
- Privacy policy URL loads and matches `website/PRIVACY.md`.
- Data safety says the developer collects nothing. Billing is disclosed as a Play purchase, not as data you collect.
- Content rating certificate is attached.
- `versionCode` is higher than any bundle already uploaded.
