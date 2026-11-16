# Racing Wheel Pro — website

Promotional site for [Racing Wheel Pro](https://github.com/EarthQuakEmc2/RacingWheel_V1.0): an Android phone used as a sim racing wheel, plus a Windows receiver. The page is React, TypeScript, and Vite. The hero wheel is drawn in the browser with three.js (react-three-fiber). It does not download a 3D model. If WebGL is unavailable, a built-in SVG wheel is shown instead.

## Run locally

From this folder:

```bash
npm install
npm run dev
```

Vite prints a local URL, usually http://localhost:5173/. The dev server uses base `/`, so that address is the whole site.

## Production build

```bash
npm run build
npm run preview
```

`npm run build` checks TypeScript, then builds with Vite `base` set to `/RacingWheel_V1.0/`. That is the path GitHub project Pages uses. `npm run preview` serves the production build, so open:

http://localhost:4173/RacingWheel_V1.0/

The published site is:

https://earthquakemc2.github.io/RacingWheel_V1.0/

## Deploy

Pushes to `main` that touch `website/` run `.github/workflows/pages.yml`. The workflow installs dependencies, runs `npm run build`, and deploys `website/dist` with GitHub Actions. Commits that only change the Android app or the Windows receiver do not start this workflow. You can also run it by hand from the Actions tab (`workflow_dispatch`).

One-time setup, in the repository on GitHub:

1. Open **Settings → Pages**.
2. Under **Build and deployment**, set **Source** to **GitHub Actions**.

The first successful run publishes the site. Until that source is selected, the workflow can build and then fail at the deploy job.

Screenshots on the page are the files in `docs/screenshots/`. They are imported by the build, so the site and the repository README stay on the same images.
