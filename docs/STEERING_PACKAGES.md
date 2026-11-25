# Steering Packages

This branch is a draft. Do not merge it, and do not deploy it to the Cloudflare Worker `racingwheel-pro` or to `myracingwheel.site`. The owner turns it on later.

It is separate from Play Billing on `paid-version` (PR #4). That branch sells one non-consumable, `racing_wheel_premium`. This branch sells manufacturer packs the way Assetto Corsa sells car packs. The offline rule is the same idea as `EntitlementPolicy` on that branch: a finished check replaces the cached licence, and a check that never finishes keeps the last licence that verified.

## Catalogue

`catalogue/steering-packages.json` is the only price list.

| Product | Price |
| --- | --- |
| One manufacturer | $3.99 |
| Any 3 manufacturers | $9.99 |
| Any 6 manufacturers | $14.99 |
| Any 9 manufacturers | $24.99 |
| Every manufacturer | $34.99 |
| Formula 1, GT1, GT2, GT3, GT4, GT5, M1, M2, M3, M4 | `priceStatus: "TODO"` |

A class pack cannot be checked out while `priceStatus` is `TODO`. To price one, set `priceCents` to an integer number of cents and `priceStatus` to `set`.

Manufacturers are the official Kunos Assetto Corsa makes: Abarth, Alfa Romeo, Audi, BMW, Chevrolet, Ferrari, Ford, KTM, Lamborghini, Lotus (including Classic Team Lotus), Maserati, Mazda, McLaren, Mercedes-Benz, Nissan, Pagani, Porsche, Praga, RUF, Scuderia Glickenhaus, Shelby, Tatuus, and Toyota. `templates` is the list of wheel ids a purchase unlocks. `includedFree` stays free without a purchase. The four free wheels are `moza_ks`, `ferrari_sf1000`, `mercedes_amg_f1`, and `mclaren_ascher_dark`.

M1, M2, M3, and M4 are placeholder classes. Assetto Corsa does not ship categories with those names. Their template lists are empty on purpose.

After editing template ids, rebuild the hashes:

```bash
node worker/scripts/hash-packs.mjs
```

That rewrites `catalogue/pack-manifest.json` and copies template JSON into `website/public/packs/files/`. PNG files stay in `website/public/wheels/`.

## How a purchase becomes a wheel

1. The site quotes the cart. The browser does not get to name the price.
2. `POST /api/checkout` stores an order: account id, package ids, amount, currency, provider reference. There is no column for a card number, expiry, or security code. A body that contains card fields is rejected.
3. The default provider is `sandbox`. Confirming the order marks it paid and writes one entitlement row per package.
4. The worker signs a licence with Ed25519 over a canonical JSON payload: account id, package ids, template ids, and the sha256 of each pack file. There is no expiry. The phone keeps working offline with the last signature that verified.
5. The app downloads each file only if the account owns the package, checks the hash against the signed list, and writes the file under the app's private `steering-packs/` directory. A licence whose signature fails is ignored. A file whose hash fails is ignored. Free wheels never consult the licence.

The committed key pair is a development key. `LICENCE_MODE=dev` is required before the worker will sign with it. Production mode refuses that key. The private key is in `worker/dev-keys/DEV_ONLY_licence_private.b64` and `worker/.dev.vars.example`. The matching public key is `catalogue/dev-licence-public.b64`, and the app reads it from assets. Anyone with the repository can forge a licence that this development public key accepts. Replace both halves before a real launch.

Wheels that are not in any pack (the paid MOZA plates) still follow the existing premium gate. On this branch the open premium flavor does not bypass a pack template.

## Payment provider

Use Stripe Checkout, and only its test mode, until you deliberately go live.

Stripe is the right adapter for this catalogue because a buyer picks any 3, 6, or 9 manufacturers. Checkout can charge that combination as one payment without pre-creating every combination as a product. The card is entered on Stripe's page. This server never sees it. Test keys are free, and webhooks are how the worker learns that a payment succeeded before it signs a licence.

The adapter refuses `sk_live_` and `rk_live_`. It accepts `sk_test_` only. Leave `PAYMENT_PROVIDER` unset, or set it to `sandbox`, until a test key exists. Sandbox confirm does not call Stripe.

Lemon Squeezy is the alternative if you want a merchant of record to handle VAT. Its catalogue is a worse fit for "any 3 of these", because each combination wants its own product. Keep Stripe unless tax handling is the thing you want to buy.

## What you do before launch

1. Do not deploy this branch to `racingwheel-pro`.
2. Create a new Ed25519 key. Put the PKCS#8 private key in the Worker secret `LICENCE_PRIVATE_KEY_PKCS8`. Leave `LICENCE_MODE` unset so the dev key cannot sign. Replace `catalogue/dev-licence-public.b64` with the new raw 32-byte public key (standard base64) and ship that in the app.
3. Set real prices on the class packs, or leave them `TODO` so they stay unsellable.
4. Add the missing wheel PNG and JSON files, list their ids in the catalogue, and run `node worker/scripts/hash-packs.mjs`. Manufacturers with an empty `templates` array can be purchased, but they install no plates until you add some. Decide whether that is acceptable before taking real money.
5. Apply `worker/migrations/0002_steering_packages.sql` to a database that is not the production D1, and prove sandbox checkout there.
6. Open a Stripe account. Create a test secret (`sk_test_`) and a test webhook secret. Set `PAYMENT_PROVIDER=stripe` only on the non-production worker. Point the webhook at `POST /api/payments/webhook`.
7. When you are ready for real charges, that is a separate step this branch does not implement: a live key is refused on purpose. You would relax that guard in a later change, still without storing card data, and only after the dev licence key is gone.
8. Read `play-store/PUBLISHING.md` before any listing uses manufacturer names.

Local sandbox, from `worker/`, after `cp .dev.vars.example .dev.vars`:

```bash
npx wrangler d1 execute racingwheel-pro --local --file=migrations/0001_init.sql
npx wrangler d1 execute racingwheel-pro --local --file=migrations/0002_steering_packages.sql
npx wrangler dev --local
```

The site dev server proxies `/api` to port 8787. In the app, `-Pracingwheel.apiBase=http://10.0.2.2:8787` points an emulator at that machine. Release builds talk to `https://myracingwheel.site`, which does not have these routes until you deploy them yourself.

## Tests

```bash
cd worker && npm test
cd website && npx tsc --noEmit
```
