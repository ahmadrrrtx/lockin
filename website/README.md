# Lockin website (`website/`)

A single-page, dependency-free site for the Lockin app: original content, the
app's own design tokens, light/dark themes, and self-contained 3D effects
(CSS + vanilla JS — no CDNs, works offline).

## Preview locally

```bash
cd website
python3 -m http.server 8080
# open http://localhost:8080
```

(Or just open `index.html` in a browser — everything is relative.)

## Where to edit what

| You want to change… | Edit |
|---|---|
| Colours, radii, shadows, fonts | top of `styles.css` → `:root` and `[data-theme="dark"]` (keep in sync with `branding/SPEC.md`) |
| Logo / wordmark | `.brand` blocks in `index.html` (inline SVG — brand geometry from `branding/SPEC.md`) |
| Hero badge, headline, copy, buttons | `<!-- EDIT: hero -->` section in `index.html` |
| Trust strip facts | `<!-- EDIT: trust strip -->` |
| How-it-works steps | `<!-- EDIT: how it works -->` |
| Strict-mode band + state machine | `<!-- EDIT: strict spotlight -->` |
| App preview phones (text inside the mock UIs) | `<!-- EDIT: preview -->` |
| Feature cards | `<!-- EDIT: features grid -->` |
| Privacy list / manifest card | `<!-- EDIT: privacy band -->` |
| Download card (version, size, links) | `<!-- EDIT: get beta -->` |
| FAQ answers | `<!-- EDIT: FAQ -->` |
| Footer links / attribution | `<!-- EDIT: footer -->` |
| Hero artwork (`assets/hero-mockup.png`) | `python3 scripts/render_hero.py` (needs Pillow) |
| Page title / meta / social preview | `<head>` of `index.html` |

**Release checklist after changing the download card:** bump version text in
the hero chip, trust strip, download card and `og:` tags together.

## Publishing

The repo ships `.github/workflows/pages.yml`: every push to `main` that
touches `website/` redeploys automatically once you enable it:

> GitHub repo → **Settings → Pages → Build and deployment → Source: GitHub Actions**

Your site then lives at `https://ahmadrrrtx.github.io/lockin/`.

## House rules

- **No fabricated screenshots.** The preview phones are labelled interface
  previews drawn from the design system; real device captures go through
  `docs/SCREENSHOT_PROCEDURE.md` and replace them honestly.
- **No claims the app can't keep** (device admin ≠ device owner, no
  "uninstall-proof" wording).
- **No trackers / no CDN dependencies** — same offline-first spirit as the app.
