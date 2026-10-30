<p align="center">
  <img src="https://raw.githubusercontent.com/ahmadrrrtx/lockin/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" alt="Lockin icon" />
</p>

# Lockin

[![CI](https://github.com/ahmadrrrtx/lockin/actions/workflows/ci.yml/badge.svg)](https://github.com/ahmadrrrtx/lockin/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/ahmadrrrtx/lockin?label=release)](https://github.com/ahmadrrrtx/lockin/releases/latest)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/license-GPL--3.0--or--later-blue.svg)](LICENSE)
[![minSdk 23 (Android 6.0)](https://img.shields.io/badge/minSdk-23%20%C2%B7%20Android%206.0+-3DDC84.svg)](docs/ANDROID_COMPATIBILITY.md)
[![offline · no INTERNET permission](https://img.shields.io/badge/network-none-brightgreen.svg)](PRIVACY_POLICY.md)

**Strict focus commitment for Android.** Choose the apps you want locked away,
choose a duration, commit — and from that moment until the deadline there is
*no* cancel, no pause, no shorten, and no early unlock. Not in the UI, not from
a notification, not via a broadcast or deep link. When the timer completes, the
lock releases by itself.

|  |  |
| --- | --- |
| **Package** | `com.rrrtx.lockin` · v1.1.0 (versionCode 2) |
| **Android support** | 6.0 (API 23) → 16 · see [compatibility matrix](docs/ANDROID_COMPATIBILITY.md) |
| **Privacy** | offline-first: **no INTERNET permission**, no accounts, no analytics — local SQLite only |
| **Signing** | one universal APK, v1 + v2 signature schemes |
| **License** | GPL-3.0-or-later · [LICENSE](LICENSE) · upstream attribution in [NOTICE](NOTICE) |

### Why Lockin Stands Out

Most "focus" tools quietly offer escape hatches — a pause button, a
"just five more minutes", a sideloaded bypass. Lockin removes the concept:
the session state machine (`IDLE → PREPARING → ACTIVE → COMPLETED`) has **no
early-exit transition to hide**, because it has no early-exit transition at all.
The guarantee is mechanical, not cosmetic, and `scripts/verify_contract.sh`
re-proves it on every release. Second, Lockin is offline-first by construction:
the merged manifest contains no `INTERNET` permission, so phone-home tracking
isn't a policy promise — it's impossible.

### Screenshots

No screenshots are shown because none were captured on a real device yet —
Lockin doesn't pass mockups off as real UI. Want to fix that? Follow
[`docs/SCREENSHOT_PROCEDURE.md`](docs/SCREENSHOT_PROCEDURE.md) with your phone
and open a PR — real captures are gratefully merged.

### Platforms

- Android 6.0 – 16 (this repository)

### How to install

1. Download **`Lockin-Android.apk`** from [the latest release](https://github.com/ahmadrrrtx/lockin/releases/latest).
2. Verify the checksum against `dist/SHA256SUMS.txt`.
3. Follow the step-by-step sideload guide: **[`dist/INSTALL.md`](dist/INSTALL.md)**
   (includes the Android 13+ *restricted settings* step for accessibility).

**Seeing "App not installed" or "App blocked to protect your device"?**
That's Google Play Protect's automatic block on browser-sideloaded apps that
declare accessibility (Lockin's core) — the APK is fine. Use Route A/B/C in
[`dist/INSTALL.md`](dist/INSTALL.md) §0 (pause Play Protect → install →
resume; or adb; or Google's unverified-packages flow). For the exact
PackageManager error instead, run
[`scripts/diagnose_install.sh`](scripts/diagnose_install.sh) with a phone connected.

### In-Depth Feature Analysis

#### Strict Sessions & Timing

- **Engine-enforced sessions:** `IDLE → PREPARING → ACTIVE → COMPLETED` lives
  in pure Kotlin (`core/SessionState.kt`); UI can only request legal events.
- **Presets and custom durations:** 15/25/30/50/60/120/240 min plus custom up
  to 7 days, with the exact end time shown on the commitment screen.
- **Schedules:** time-based automatic starts with dedup, shown with next-run
  previews.
- **Timer integrity:** elapsed-realtime primary + persisted wall deadline;
  clock jumps are corrected *honestly* (DEGRADED flag, never a silent reset).
- **Recovery:** survives process death, force-stop, reboot, and OEM task
  killers via SQLite state, boot receivers, alarms, and a 15-minute watchdog.

#### Blocking & Protection

- **Accessibility detection:** window-state events only
  (`TYPE_WINDOWStateChanged`) with `canRetrieveWindowContent=false` — Lockin
  never reads what's on your screen.
- **Block page:** full-screen countdown, HOME press with a 600 ms cool-off,
  per-package attempt throttling.
- **Never-block allowlist:** phone/dialer, launcher, SystemUI, package
  installer, telecom — and Lockin itself is never blockable.
- **Protection center:** an honest dashboard of what is enabled, what is
  optional, and what a determined user could still disable — no Device Owner
  claims Lockin doesn't have.
- **Device admin (optional):** one deliberate extra step before uninstall;
  `setUninstallBlocked` is used **only** when genuinely provisioned as Device
  Owner (documented, never faked).

#### Privacy & Platform

- **Impossible to phone home:** no `INTERNET` permission — verified against the
  merged manifest by CI/contract checks.
- **Complete permission list** (and nothing else): app inventory, notifications,
  boot recovery, foreground services, exact alarms, optional battery exemption —
  table in [PRIVACY_POLICY.md](PRIVACY_POLICY.md).
- **Version-aware support:** channels guarded for Android 8, notification
  permission for 13+, FGS types for 14+, edge-to-edge insets for 15+, targets
  Android 16 — matrix in [docs/ANDROID_COMPATIBILITY.md](docs/ANDROID_COMPATIBILITY.md).

#### For Developers

- **[`agents.md`](agents.md):** repository invariants, build/verify commands,
  and the architecture map for human and AI contributors.
- **[`scripts/verify_contract.sh`](scripts/verify_contract.sh):** mechanical
  proof that offline mode, no-unlock transitions, notification behavior, brand
  identity, and accessibility minimization still hold.
- **Docs set:** [product spec](docs/LOCKIN_PRODUCT_SPEC.md) ·
  [architecture](docs/LOCKIN_ARCHITECTURE.md) ·
  [threat model](docs/LOCKIN_THREAT_MODEL.md) ·
  [testing status](docs/TESTING.md) ·
  [compatibility](docs/ANDROID_COMPATIBILITY.md).

### Verification status (honest)

| Check | Result |
| --- | --- |
| Unit tests | 52 unique tests × debug+release = 104 executions, **0 failures** |
| Android Lint (minSdk 23 gate) | **0 errors** (`NewApi` 0), warnings triaged in [docs/TESTING.md](docs/TESTING.md) |
| Product contract | **all checks pass** (offline, no-unlock, no notification actions, brand, a11y config) |
| Release signature | v1 + v2 verified, cert SHA-256 in [dist/MANIFEST.md](dist/MANIFEST.md) |
| On-device runtime tests | **not yet executed on this build host** — matrix rows stay ❌ until someone runs [§7 of the compatibility doc](docs/ANDROID_COMPATIBILITY.md) |

### Contributing

We welcome contributions — the fastest wins are screenshots, device-matrix
runs, and lint triage (see [CONTRIBUTING.md](CONTRIBUTING.md)).

1. Fork the repository.
2. Create a branch for your fix or feature.
3. Keep `./gradlew test lint assembleDebug` and `scripts/verify_contract.sh` green.
4. Push and open a Pull Request stating what changed and what you tested.

### License

Lockin is free software licensed under **GPL-3.0-or-later** — see [LICENSE](LICENSE).
It was developed from an engineering study of [Curbox](https://github.com/curbox-app/curbox-android);
GPL obligations, upstream attribution and the full change record live in [NOTICE](NOTICE).
