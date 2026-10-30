# Lockin — Android Compatibility (v1.1.0)

**Honesty rule for this document:** *tested* means executed on a real device or
emulator. The build host used for v1.1.0 has **no connected device and no
hardware-accelerated emulator** (no KVM), so **no runtime device tests were
performed** for any Android version — including Android 9. Everything claimed
below as verified was verified *statically* (toolchain, bytecode lint, manifest
and signature inspection); everything that still needs a device says so
explicitly. The manual test script and `scripts/diagnose_install.sh` exist so a
human can close that gap in minutes.

## 1. Compatibility floor (what and why)

| | |
|---|---|
| **minSdk** | **23 — Android 6.0 Marshmallow** |
| targetSdk | 35 — Android 15 (works on Android 15 & 16; see §4) |
| compileSdk | 35 |
| APK | one universal file, no native code, no split APKs required |
| Signatures | **v1 (JAR) + v2** — v1 is mandatory for API 23–25 installs |

### Why API 23 is the lowest *defensible* minimum

Evidence-based, not guessed:

1. **Full dependency graph scanned** — every AndroidX/material/constraint AAR in
   the resolved graph declares `minSdk ≤ 21` (activity 19, lifecycle 19,
   material 19, core 21, fragment 21, appcompat 21, constraintlayout 21,
   recyclerview 14). No dependency forces a higher floor; the manifest merger
   accepts 23 with zero overrides.
2. **Everything Lockin's reliability model needs exists at API 23:**
   runtime permissions (the permission era our request flows are written for),
   `AlarmManager.setExactAndAllowWhileIdle` (doze-aware exact completion —
   the floor of this exact API is 23), `JobScheduler` (watchdog, 21+),
   `PendingIntent.FLAG_IMMUTABLE` (23+), `NotificationCompat` channels
   (guarded ≥26 below), accessibility blocking (4.0+).
3. **Bytecode-level audit at minSdk 23:** Android Lint's `NewApi` check ran
   against minSdk 23 over the whole source set and reports **0 errors**
   (`InlinedApi` also 0 — the one pre-23-era field reference was rewritten to a
   literal key with a default). This is the platform's own API-availability
   checker, not a manual guess.
4. **Why not lower than 23:** below 23 sits the pre-M permission model and
   pre-doze alarm semantics — none of which Lockin's flows (post-M permission
   requests, doze-aware exact completion, immutable pending intents) are
   designed or supported for, and none of which can be verified on this build
   host. Raising *or* lowering minSdk to hide errors was explicitly avoided:
   the floor moved 26 → 23 **because all APIs were then made version-aware**
   (§3), not the other way around.

## 2. Compatibility matrix

"Static verification" = signature scheme available to that API, minSdk ≤ API,
manifest/bytecode checks passed. "Runtime" = executed on device — **none were.**

| Android | API | Static verification | Runtime tested | Version-specific handling in Lockin |
|---|---|---|---|---|
| 6.0/6.0.1 | 23 | ✅ installable (v1 sig, minSdk 23, `NewApi`=0) | ❌ not tested | exact alarms native; permission requests auto-grant (pre-M model N/A — 23 *has* runtime perms); no channels needed |
| 7.0/7.1 | 24–25 | ✅ same | ❌ not tested | as above; boot-count setting read via literal key (field is API 24) |
| 8.0/8.1 | 26–27 | ✅ same | ❌ not tested | notification channels created (guarded `≥ O`); `startForegroundService` path via `ContextCompat` |
| **9** | **28** | ✅ same + `FOREGROUND_SERVICE` permission declared (required from 28) | ❌ not tested — **device test steps in §5** | 2-arg `startForeground` (no FGS types before 29); a11y + block screen unaffected |
| 10 | 29 | ✅ | ❌ not tested | FGS types exist but are optional; Lockin keeps 2-arg start below 34 |
| 11 | 30 | ✅ | ❌ not tested | package visibility: `QUERY_ALL_PACKAGES` declared; below 30 the permission is unknown and enumeration works natively |
| 12/12L | 31–32 | ✅ | ❌ not tested | `canScheduleExactAlarms()` gate with inexact-alarm fallback; `FLAG_IMMUTABLE` already used everywhere |
| 13 | 33 | ✅ | ❌ not tested | `POST_NOTIFICATIONS` runtime request guarded `≥ TIRAMISU`; sideloaded-app *restricted settings* for accessibility documented in `dist/INSTALL.md` |
| 14 | 34 | ✅ | ❌ not tested | FGS type enforcement: `specialUse` declared in manifest **and** passed in `startForeground(...)` guarded `≥ UPSIDE_DOWN_CAKE` |
| 15 | 35 | ✅ targetSdk matches | ❌ not tested | edge-to-edge enforced at target 35: insets handled via `ViewCompat` in `MainActivity`; no FGS-type timeouts apply (`specialUse` is exempt) |
| 16 | 36 | ✅ targetSdk 35 forward-compatible | ❌ not tested | predictive back not opted-in (legacy back path — supported); no API-36-only behaviours relied on |

**Future-proofing stance:** the engine core (SQLite, alarms, job scheduler,
accessibility callbacks) uses stable platform contracts; new platform releases
are absorbed by (a) staying current on targetSdk for Play-era policy,
(b) the `NewApi` lint gate on every build, and (c) explicit `SDK_INT` guards at
each behavioural seam — the pattern already used for API 23/24/26/28/29/31/33/34.

## 3. Version-aware implementation audit

Every seam, as implemented in v1.1.0 (file references are in `app/src/main/java`):

| Concern | API | Handling |
|---|---|---|
| Notification channels | 26+ | `Notifications.ensureChannels` early-returns below `O`; `NotificationCompat.Builder` drops channel ids silently pre-26 |
| Foreground service start | 26+ | `ContextCompat.startForegroundService` (startForegroundService ≥26, startService below); `startForeground` runs in `Service.onCreate` |
| FGS permission + type | 28 / 34 | permission in manifest (unknown-but-harmless below 28); 3-arg `startForeground(SPECIAL_USE)` only `≥ 34` (`SessionService`) |
| Process name | 28+ | `Application.getProcessName()` guarded `≥ P`; API 23–27 fallback via own-pid lookup in `runningAppProcesses`, then `applicationInfo.processName` (`LockinApp`) |
| Exact alarms | 23 / 31 | `setExactAndAllowWhileIdle` (native at floor); `canScheduleExactAlarms()` gated `≥ S` with inexact fallback (`SessionAlarms`, `Ui.kt`) |
| Notification permission | 33+ | `POST_NOTIFICATIONS` requested only `≥ TIRAMISU`; auto-granted below (`OnboardingFragment`, `SettingsFragment`, `Ui.kt`) |
| Package visibility | 30+ | `QUERY_ALL_PACKAGES`; native enumeration below 30 |
| Boot recovery | all | `BOOT_COMPLETED` + `QUICKBOOT_POWERON` receivers (all supported APIs); boot-count integrity read via literal setting key with default |
| Receiver registration | 33/34 | all context-registered receivers use `ContextCompat.registerReceiver` (flag-aware, safe fallback) |
| Accessibility | all | `TYPE_WINDOW_STATE_CHANGED`-only config, `canRetrieveWindowContent=false` — unchanged and valid on 23+; Android 13+ sideload "restricted settings" gate documented for users |
| Runtime permissions | 23+ | permission flows exist exactly at the floor era; below-permission-era effects impossible (floor is 23) |
| Edge-to-edge | 35 | handled by inset listeners; nothing to do pre-35 |
| Back / predictive back | 33+ opt-in | not opted in → legacy back everywhere, consistent across 23–36 |
| Launcher icon | ≤25 | adaptive XML only exists on 26+; density **PNG fallbacks** (mdpi→xxxhdpi, rounded-square legacy silhouette + circular round variant) shipped so API 23–25 launchers get a real icon |

## 4. Target SDK 35 on Android 16

Android 16 (API 36) devices install and run targetSdk-35 apps normally.
Lockin uses no API-36-only behaviour, does not lock activity orientation
(so OEM large-screen policies apply cleanly), and has not enabled predictive
back (an opt-in that would be the first thing to do when targeting 36).

## 5. The reported Android 9 install failure — root-cause analysis

### What was inspected (v1.0.0 APK + project)

| Check | Result on 1.0.0 | Verdict for Android 9 (API 28) |
|---|---|---|
| minSdk | 26 ≤ 28 | not the cause (`INSTALL_FAILED_OLDER_SDK` impossible) |
| signature schemes | **v2 only** (v1=false, v3=false) | *not blocking on 9* (v2 exists since 24) — but **was blocking for API 23–25** and weaker verifier coverage generally |
| zip alignment | OK (4-byte) | not the cause |
| `android:testOnly` | absent | not the cause |
| native libs / ABI | none (pure JVM) | `INSTALL_FAILED_NO_MATCHING_ABIS` impossible |
| package identity | `com.rrrtx.lockin`, single APK, valid ZIP (91 entries/2 dex) | parse failures unlikely |
| versionCode/Name | 1 / 1.0.0 | downgrade only if a **higher** code already existed on the phone |
| manifest | targetSdk 35, all `android:exported` explicit, FGS attrs are ignored-when-unknown below their API | no API-28 parse blockers |
| storage/delivery | 4.73 MB file | corrupt copy or low storage remain device-side possibilities |

**CONFIRMED root cause (screenshots, 2026-10-30):** **Google Play Protect**
blocked the install. The device showed *"App blocked to protect your device —
This app can request access to sensitive data. This can increase the risk of
identity theft or financial fraud"* (OK-only button), followed by *"App not
installed."* — Google's **internet-sideloading rule** from the
[official Play Protect developer guidance](https://developers.google.com/android/play-protect/warning-dev-guidance):
APKs obtained from browsers, messaging apps, or file managers that declare
`RECEIVE_SMS` / `READ_SMS` / notification-listener / **`ACCESSIBILITY`**
permissions are **automatically blocked at install time**. Lockin declares an
accessibility service by design, so a browser download on any Play-Protect
phone reproduces the failure — **on every Android version**, not only 9.
The static analysis above stands: **no APK defect exists**; the block is GMS
policy executing as designed, and no APK change can remove the trigger without
removing the blocker feature itself.

**Install routes not subject to the block:** `adb install`, temporarily
disabling Play Protect scanning for one install, Google's 2026 "allow
unverified packages" developer flow, or store session-installs — step-by-step:
`dist/INSTALL.md` §0. (The APK is unchanged: v1.1.0.)

**None of the code-level causes were reproducible** — which is exactly why
v1.1.0 does not "guess-fix" random SDK values; instead it hardened everything
that *was* provably wrong (see §6) and ships `scripts/diagnose_install.sh` to
capture the exact error code from the phone:

```bash
# computer with the phone connected (USB debugging on):
scripts/diagnose_install.sh dist/Lockin-Android.apk
```

The script runs the requested sequence — `adb devices -l`,
`adb install -r`, and filtered `adb logcat` for PackageManager/PackageInstaller —
and prints the failing error code with the matching fix. It **never uninstalls
or deletes user data**; on a signature conflict it explains the choice instead.

### What v1.1.0 changed to remove every plausible cause

| Change | Defeats |
|---|---|
| **v1 + v2 signing** (verified `v1: true, v2: true`) | any verifier rejecting v1-less APKs; installs on API 23–25 where 1.0.0 would have been rejected outright |
| **versionCode 2 / 1.1.0** | same-code update quirks; makes clean-vs-update installs distinguishable in logs |
| minSdk **23** + full version-aware audit | Android 6+ range demand; removes *all* unguarded new-API calls (lint `NewApi` 0) |
| density launcher PNGs | API ≤25 broken/missing launcher icon after the floor change |
| SHA256SUMS + diagnose script | corrupted-transfer dead end |

## 6. Feature differences on older devices (honest list)

| Behaviour | Where it differs |
|---|---|
| Notification channels (per-channel mute, badges) | Android ≤8.1: system has no channels — notifications still appear, without channel grouping |
| Exact-alarm user toggle | Android ≤11: no `SCHEDULE_EXACT_ALARM` setting exists — Lockin schedules exact alarms unconditionally (works natively) |
| POST_NOTIFICATIONS prompt | Android ≤12: granted at install — onboarding skips the prompt |
| Accessibility "restricted settings" for sideloaded apps | Android 13+ only — documented with the exact settings path in `dist/INSTALL.md` |
| FGS `specialUse` type | Android ≤13: types don't exist; Android 14+: enforced — handled on both sides |
| Launcher icon shape | Android ≤7.1: rounded-square PNG variant (adaptive icons didn't exist); 8.0+: adaptive XML with monochrome/themed layer |
| Blocking UX | unchanged across all supported versions (accessibility window-state events are stable since API 4) |

**Not claimed possible on any version:** defeating OEM task killers, unlocking
a strict session early, or surviving a user who factory-resets the phone —
see `docs/LOCKIN_THREAT_MODEL.md`.

## 7. Runtime test plan (NOT yet executed)

On any machine with a device/emulator (the numbers match the required matrix):

1. `scripts/diagnose_install.sh dist/Lockin-Android.apk` → install, capture errors if any.
2. Launch → onboarding → enable blocker (Accessibility) → grant notifications (13+).
3. Confirm installed-app list populates → start a 15-min session → attempt to open a
   blocked app → verify block screen + countdown → verify notification ticks.
4. Force-stop Lockin (app info) → reopen → session still ACTIVE (process-death
   recovery via `:lockin_core`, watchdog, and alarms).
5. Reboot mid-session → session resumes; completion fires after deadline
   (wall-clock persistence + boot receivers).
6. Wait for expiry → session COMPLETES with no unlock path during ACTIVE.
7. Repeat 1–6 on: Android 9 (mandatory), plus one each of 6, 10, 11, 12, 13,
   14, 15, 16 as available; record results in the table of §2 replacing ❌.

`docs/SCREENSHOT_PROCEDURE.md` covers the same ground for capturing real
screenshots. **Until those runs happen, this document claims zero runtime tests.**
