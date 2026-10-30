# Lockin

[![CI](https://github.com/ahmadrrrtx/lockin/actions/workflows/ci.yml/badge.svg)](https://github.com/ahmadrrrtx/lockin/actions/workflows/ci.yml)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/license-GPL--3.0--or--later-blue.svg)](LICENSE)
[![minSdk 23 (Android 6.0)](https://img.shields.io/badge/minSdk-23%20%C2%B7%20Android%206.0+-3DDC84.svg)](docs/ANDROID_COMPATIBILITY.md)
[![version 1.1.0](https://img.shields.io/badge/version-1.1.0-orange.svg)](dist/MANIFEST.md)
[![offline · no INTERNET permission](https://img.shields.io/badge/network-none-brightgreen.svg)](PRIVACY_POLICY.md)

**Strict focus commitment for Android.** Pick the apps you want locked away,
pick a duration, commit — and while the session is running there is *no* cancel,
no pause, no shorten, no early unlock, from any surface: not the UI, not a
notification, not a broadcast, not a deep link. When the timer completes, the
lock releases by itself.

| | |
|---|---|
| **Package** | `com.rrrtx.lockin` · v1.1.0 (versionCode 2) |
| **Android support** | 6.0 (API 23) → 16 · see [`docs/ANDROID_COMPATIBILITY.md`](docs/ANDROID_COMPATIBILITY.md) |
| **Privacy** | offline-first: **no INTERNET permission**, no accounts, no analytics, no backend — local SQLite only |
| **Signing** | release APK signed with v1 + v2 schemes (one universal APK, no splits) |
| **License** | GPL-3.0-or-later · [`LICENSE`](LICENSE) · upstream attribution in [`NOTICE`](NOTICE) |

## Features

| | |
|---|---|
| 🔒 **Strict sessions** | `IDLE → PREPARING → ACTIVE → COMPLETED` enforced in engine code (`core/SessionStateMachine.kt`) — early termination transitions are not *hidden*, they do not exist |
| ⏳ **Focus timer** | presets 15/25/30/50/60/120/240 min, plus custom up to 7 days; exact end time shown before you commit |
| 🛡 **App blocking** | accessibility-driven detection (`TYPE_WINDOW_STATE_CHANGED` only — never reads screen content) + full-screen block page; HOME-gated relaunch with a 600 ms cool-off |
| 🧰 **Protection center** | honest dashboard: what is enabled, what an attacker (or tired you) could still disable, and which protections Lockin *cannot* promise |
| ⏰ **Schedules** | time-based automatic session starts with dedup |
| 💾 **Survives everything** | sessions persist in SQLite and recover from process death, reboot, and clock changes (elapsed-realtime primary, wall-clock deadline persisted, integrity checks — a clock jump shortens or extends honestly, never silently cheats) |
| 🚨 **Emergency access** | phone/dialer, launcher, SystemUI, package installer, telecom are never blockable — Lockin itself is never blocked either |
| 🎨 **Design** | original hourglass identity, full light/dark system, Material 3 baseline, light-on-privacy copy throughout |

## Install

Grab **`dist/Lockin-Android.apk`** (one universal APK — no split APKs), verify
its checksum against `dist/SHA256SUMS.txt`, and sideload it. Full walkthrough
(first run, Accessibility enablement, Android 13+ *restricted settings*, device
admin) is in **[`dist/INSTALL.md`](dist/INSTALL.md)**.

Quick path: copy the APK to the phone → open → allow "install from this
source" → Install → open Lockin → follow onboarding.

> **Install problem?** `scripts/diagnose_install.sh dist/Lockin-Android.apk`
> prints the exact PackageManager error code from your device and the matching
> fix — much more useful than "App not installed".

## Permissions (complete list)

| Permission | Why |
|---|---|
| `QUERY_ALL_PACKAGES` | list installed launchable apps to select & block |
| `POST_NOTIFICATIONS` | countdown, results, protection warnings (requested on Android 13+) |
| `RECEIVE_BOOT_COMPLETED` / `QUICKBOOT_POWERON` | restore active sessions after reboot |
| `FOREGROUND_SERVICE` (+ `SPECIAL_USE` on 14+) | keep the countdown honest while backgrounded |
| `SCHEDULE_EXACT_ALARM` | precise completion (graceful inexact fallback if denied) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | optional; recommended on aggressive battery managers |

**Not requested:** `INTERNET`, overlay (`SYSTEM_ALERT_WINDOW`), NFC, location,
contacts, storage. Accessibility is enabled *by the user in system settings*
(the service config is window-state-only: no content, no text, no logs).

## Screenshots

No screenshots ship in this repository: the v1.1.0 build host has **no device
and no emulator** (no KVM), and Lockin does not pass mockups off as real UI.
Capture real screenshots with
[`docs/SCREENSHOT_PROCEDURE.md`](docs/SCREENSHOT_PROCEDURE.md) (adb commands +
checklist) on any machine with a phone attached.

## Architecture (one paragraph)

Kotlin, single-activity fragment shell (UI process) plus an isolated
`:lockin_core` process holding the blocking service, session service,
watchdog job and receivers — so the engine keeps enforcing even if the UI dies.
State machine + time guard + schedule planner are pure Kotlin (JVM-tested,
52 tests), storage is SQLite/WAL, alarms are `setExactAndAllowWhileIdle` with
inexact fallback, and a 15-minute persisted watchdog job reconciles everything
after OEM interference. Enforcement = accessibility window-state filter (5 s
cache) → HOME press + BlockActivity; notifications are countdown-only and
carry **no** actions that can affect a session.

```
app/src/main/java/com/rrrtx/lockin/
├── core/         state machine, engine, storage, alarms, notifications, watchdog
├── protection/   device admin, allowlists, roles
├── data/         preferences, drafts, app inventory
└── ui/           MainActivity + fragments (home, selection, duration, confirm,
                  active, block, history, schedules, settings, protection)
```

## Verification

```bash
./gradlew test lint assembleDebug      # unit tests + API-level lint
scripts/verify_contract.sh             # offline/branding/unlock-surface contract
```

Latest run on this tree: **52 unique tests × debug+release = 104 executions, 0 failures**;
lint **0 errors** (incl. `NewApi` against minSdk 23); contract checks all pass
(no INTERNET in the merged manifest, no legacy identity, no early-unlock event
paths, no notification actions, minimized accessibility config, identity
1.1.0/minSdk 23/v1+v2 signatures). Details: [`docs/TESTING.md`](docs/TESTING.md).

## Known limitations (stated honestly)

- **No runtime device tests yet** — the build host had no device/emulator.
  Compatibility is verified statically only; the device script is
  `docs/ANDROID_COMPATIBILITY.md` §7.
- **Device admin ≠ Device Owner.** Admin makes uninstall require one extra
  deliberate step on most phones. It does *not* make uninstall impossible, and
  Lockin never claims otherwise (no owner powers are implied anywhere in-app).
- **Force-stop / OEM task killers** can pause *Lockin's* processes until the
  next trigger fires (watchdog ≤ 15 min, alarms, boot). Enforcement continuity
  during that window is bounded by what Android allows — documented per-scenario
  in [`docs/LOCKIN_THREAT_MODEL.md`](docs/LOCKIN_THREAT_MODEL.md).
- **Android 13+ sideloaded apps** need the *Restricted settings* toggle before
  accessibility can be enabled (exact path in `dist/INSTALL.md`).

## Repository layout

`app/` · `gradle/` (+ wrapper) · `branding/` (identity spec) · `docs/`
(product spec, architecture, threat model, privacy policy, compatibility,
testing, screenshot procedure, implementation plan) · `scripts/` (contract
verification, install diagnostics) · `dist/` (APK + source + install guide +
manifest + checksums) · `.github/workflows/ci.yml` (build + test + lint).

Never committed: keystores, signing passwords, `local.properties`, SDK paths —
release signing is injected via `LOCKIN_*` environment variables only.

## License

GPL-3.0-or-later — see [`LICENSE`](LICENSE).
Upstream attribution and change record: [`NOTICE`](NOTICE).
