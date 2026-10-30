# Contributing to Lockin

Thanks for your interest in improving Lockin — a strict, offline-first
focus-commitment app for Android.

## Index

1. [Quick contribute](#quick-contribute)
2. [Core principles](#core-principles)
3. [Project architecture](#project-architecture)
4. [Code style & standards](#code-style--standards)
5. [UI/UX guidelines](#uiux-guidelines)
6. [Data storage](#data-storage)
7. [Contribution workflow](#contribution-workflow)
8. [Development tips](#development-tips)

---

## Quick contribute

Easy, high-value ways to start:

### 1. Capture real screenshots

The repository ships no screenshots because none were taken on real devices
yet. Follow [`docs/SCREENSHOT_PROCEDURE.md`](docs/SCREENSHOT_PROCEDURE.md)
with any Android phone, add the PNGs under `docs/screenshots/`, and open a PR
that also updates the README screenshot section. **Real captures only** —
mockups presented as screenshots will be rejected.

### 2. Run the compatibility matrix

[`docs/ANDROID_COMPATIBILITY.md`](docs/ANDROID_COMPATIBILITY.md) §7 lists the
10-step device script (install → onboarding → blocking → countdown → expiry →
process death → reboot). Run it on a device you own, then PR a fill-in of the
"Runtime tested" column with your device, Android version, and results.

### 3. Triage the lint report

`./gradlew lint` then read `app/build/reports/lint-results-debug.txt`. The
`UnusedResources` list has strings that are genuinely unreferenced — removing
one safely (grep every usage first, rebuild) is a perfect small PR.

**Workflow for quick contributions:** open a PR → CI builds and tests →
download the artifact, verify it on your device → comment with what you tested.

---

## Core principles

- **Strictness first:** no cancel/pause/shorten/early-unlock surfaces may ever
  be added — see [`agents.md`](agents.md), this is enforced mechanically.
- **Offline first:** no `INTERNET` permission, no telemetry, ever.
- **Safety first:** the blocker and session services must never crash from a
  single failed event.
- **Honesty first:** no capability claims the code does not deliver
  (permissions, protection levels, tested platforms).
- **Verify your own changes:** if AI tools assisted you, you are responsible
  for understanding every line you submit.

## Project architecture

One `:app` module, two processes:

1. **UI process** — `MainActivity` fragment shell, adapters, preferences.
2. **`:lockin_core`** — `BlockerAccessibilityService`, `SessionService`,
   `SessionWatchdogJob`, boot/alarm receivers, `BlockActivity`.

Engine logic (`core/`) is pure Kotlin behind injected clocks so the state
machine, time guard, duration rules, schedule planner, and restriction gate
stay JVM-testable. Read [`docs/LOCKIN_ARCHITECTURE.md`](docs/LOCKIN_ARCHITECTURE.md)
before structural changes.

## Code style & standards

- Kotlin, classic Views + ViewBinding. **No Compose, no Navigation component.**
- Readability over cleverness; follow the style of the file you touch.
- Minimal comments: explain *why*, not *what*.
- Keep API-23 compatibility: guard newer APIs, keep lint `NewApi` at 0.
- Version-specific behavior goes behind `Build.VERSION.SDK_INT` checks or
  androidx compat helpers — never an unguarded call.

## UI/UX guidelines

- Design tokens live in `values/colors.xml` + `values-night/`; shape and type
  scale in `values/` — see [`branding/SPEC.md`](branding/SPEC.md).
- Monochrome foundation + exactly **one** accent. No gradients, no second hue.
- User-facing copy: first person, plain, calm; **never overstate protection**
  (e.g., device admin is "harder to uninstall", never "cannot be uninstalled").

## Data storage

- **SQLite (WAL)** for sessions, schedules, history — through
  `SessionRepository` only. Schema changes need an issue first: they affect
  persisted user data.
- **SharedPreferences (`Prefs`)** for small UI state (onboarding, theme).
- No network state. Ever.

## Contribution workflow

1. Discuss bigger changes in an issue (schema, permissions, architecture).
2. Branch → focused commits → PR against `main`.
3. Keep CI green: `test`, `lint`, `assembleDebug`, `assembleRelease`.
4. PR description must state: what changed, what you ran, what you could not run.
5. Never commit keystores, `local.properties`, tokens, or device dumps.

## Development tips

```bash
./gradlew test lint assembleDebug      # standard gate
bash scripts/verify_contract.sh        # product contract checks
```

- Release signing locally: export the four `LOCKIN_*` variables described in
  `dist/MANIFEST.md`; without them `assembleRelease` produces an explicitly
  unsigned build (fine for testing).
- Sideloaded builds on Android 13+: enable **Settings → Apps → Lockin →
  Special app access → Restricted settings** before enabling the accessibility
  service.
