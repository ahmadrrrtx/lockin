# LOCKIN — Threat Model

Scope: a single user protecting *themselves* from their own impulsive behaviour on a
normal, unmanaged Android phone. Lockin is not an anti-tamper product against a
hostile device owner — Android does not allow that, and Lockin never pretends it does.

## 1. Assets

| Asset | Why it matters |
|---|---|
| Active session integrity (duration + selected set) | the product's core promise |
| Blocking engine availability | enforcement continuity |
| Honest protection reporting | user trust; no false safety |
| Local data (history, attempts, schedules) | personal records, no cloud copy |
| Emergency/telecom availability | safety-critical, must never be blocked |

## 2. Adversary: the distracted user (expected)

| Attempt | Outcome |
|---|---|
| Opens a locked app | HOME + block screen; attempt recorded (5 s/pkg throttle) |
| Taps anything on the block screen | only "Open Lockin" → session view (no controls) |
| Looks for cancel/pause in the UI | no such transitions exist (`SessionMachine`) |
| Notification actions | none exist; content intent only |
| Broadcasts / receivers | only time-verified `completeIfDue`; cannot end early |
| Shortens duration / removes apps mid-session | append-only SQL; no delete API; gate defers weakening prefs |
| Kills UI process (swipe away) | enforcement runs in `:lockin_core`; service is sticky; DB state persists |
| Force-stops Lockin | **platform limitation**: OS cancels our alarms/jobs until next open — documented; recovery on next open/boot |
| Reboots the phone | boot receiver: verify via wall clock, re-arm, restart countdown |
| Moves clock forward | same-boot path uses monotonic clock — no early unlock |
| Moves clock backward | drift detected vs (wall, elapsed) pair; deadline corrected exactly; >120 s flagged DEGRADED in UI |
| Turns off the accessibility service (Settings) | **platform limitation**: possible; watchdog warns (6 h cooldown) while a session enforces; protection center shows live state |
| Uninstalls Lockin after removing admin | **platform limitation**: possible on unmanaged devices; admin adds friction; stated plainly |
| Uses an old challenge/QR unlock idea | no challenge/exit feature exists in this product at all |

## 3. Adversary: casual uninstall/tamper

- Device admin (optional, zero-risk policy set): deactivation required before
  uninstall on most OEMs; deactivation during an ACTIVE session raises a warning
  notification and the Protection Center reflects reality immediately.
- Device owner enhanced mode: `setUninstallBlocked` applied to Lockin + session
  packages **only** when `isDeviceOwnerApp()` is true; released on completion.
  Normal users never see claims this mode is active.
- Accessibility bounce-on-admin-screen behaviours are deliberately **not** implemented
  (they require invasive screen reading); friction + honesty is the chosen trade.

## 4. Safety boundaries (non-negotiable)

- Never blockable: emergency dialer flow, telecom stack, contacts, launcher,
  System UI, Settings, package installer, permission controller, keyboards,
  Lockin itself (static list + per-device role resolution).
- No background data collection: accessibility observes window state only; no
  screen content is read, stored, or transmitted (there is no network permission).
- No destructive DB migrations; no silent data loss.
- A feature failure inside the accessibility service is contained and logged —
  never a service crash.

## 5. Residual risks (accepted, documented)

1. Force-stop window until next app open/boot.
2. User disabling the accessibility service (detected + notified, not prevented).
3. Post-reboot forward clock set (undetectable after the fact) — mitigated by
   preferring monotonic time while the boot session continues.
4. OEM battery killers may delay inexact completion when exact alarms are denied —
   1 Hz service tick + every-event deadline check cover the common cases.
5. On a non-device-owner phone, package uninstall of selected apps cannot be
   prevented by any ordinary app.
