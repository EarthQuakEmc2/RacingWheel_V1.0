# Deploy the coach on Cloudflare (free plan)

The site at `myracingwheel.site` is the Worker `racingwheel-pro`: this module for `/api/*`, and the Vite build for everything else. Custom domains are already attached in the dashboard, so `wrangler.toml` does not declare routes.

Nothing in this repository deploys for you. The commands below are the ones to run on your machine after `npx wrangler login`.

Do not deploy the Steering Packages branch to the `racingwheel-pro` worker or to `myracingwheel.site`. That branch is a draft. Its sandbox checkout and dev licence key must stay off production. See [docs/STEERING_PACKAGES.md](../docs/STEERING_PACKAGES.md).

## 1. Build the site

```bash
cd website
npm ci
npm run build
```

That writes `website/dist`, which is the `[assets].directory` in `worker/wrangler.toml`.

## 2. Create the D1 database

From `worker/`:

```bash
npx wrangler d1 create racingwheel-pro
```

Copy the database id into `worker/wrangler.toml`, replacing `database_id`.

Apply the schema to the remote database:

```bash
npx wrangler d1 execute racingwheel-pro --remote --file=migrations/0001_init.sql
```

Local development uses the same file:

```bash
npx wrangler d1 execute racingwheel-pro --local --file=migrations/0001_init.sql
npx wrangler dev --local
```

## 3. Workers AI

`wrangler.toml` binds Workers AI as `AI`. The coach calls `@cf/openai/gpt-oss-120b`. On the free allowance that is enough for a beta (a few thousand neurons per analysis, and results are cached per session). No API key is required; the binding uses the account that owns the Worker.

If that model id is retired, change `COACH_MODEL` in `worker/src/api.ts` to another model on the free allowance and redeploy.

## 4. Deploy

```bash
cd worker
npx wrangler deploy
```

Confirm the custom domain still points at `racingwheel-pro`. A request to `/` should be the marketing page. `/account`, `/session/:id` and `/leaderboard` are the same single-page app. `/api/health` should return `{"ok":true,"version":"0.5.0"}`.

## Accounts

Register on `/account` with an email and a password of at least 8 characters. Passwords are stored as PBKDF2-SHA256 (10,000 iterations) via WebCrypto. The iteration count is deliberately modest so a login fits the Workers Free 10 ms CPU limit. Sessions are random tokens in an `HttpOnly` `SameSite=Lax` cookie (`Secure` on HTTPS).

Free accounts get 5 AI analyses per UTC day. Premium gets 40. There is no payment flow. Set the flag yourself:

```bash
npx wrangler d1 execute racingwheel-pro --remote --command "UPDATE users SET premium = 1 WHERE email = 'you@example.com'"
```

Cached analyses do not spend quota. When the quota is used up, the API still returns the deterministic corner notes and does not call the model.

## Link a PC

1. Run `RacingWheelPro-PC.exe`.
2. Open the logs window at `http://127.0.0.1:41513/` (also linked from the console) and choose **New code**.
3. Sign in on the website and enter that code.
4. Turn on **Upload sessions automatically** if the PC should send a log when a session ends.

Logs are gzipped JSON and CSV in `%AppData%\RacingWheelPro\Logs`. Upload is off until that switch is on.

## What not to enable

R2 is not used. Traces are downsampled to at most 1,000 points and stored in D1. Do not add a paid Worker, a paid AI gateway, or a third-party coach API.

## Bundle without deploying

From `worker/`, after the site is built:

```bash
npx wrangler deploy --dry-run --outdir ../dist/worker-bundle
```

The release artifact packs that bundle, `website/dist`, and `worker/migrations`.
