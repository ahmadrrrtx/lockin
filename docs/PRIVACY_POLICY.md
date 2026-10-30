# Privacy Policy — Lockin

**Effective date:** 2026-10-30 · **App:** Lockin (`com.rrrtx.lockin`)

## Summary

Lockin works entirely on your device. It has **no Internet permission**, no account,
no server, no analytics, no advertising and no tracking. Nothing you do in Lockin is
uploaded anywhere, because the app cannot make network requests.

## Permissions and what they are used for

| Permission / capability | Why |
|---|---|
| Accessibility service (`Lockin Focus Blocker`) | Detects which app is in the foreground so Lockin can show its block screen when a locked app opens during an active session. Configured for `typeWindowStateChanged` only, with window-content retrieval **disabled**. Screen content is never read, logged, stored or transmitted. |
| `QUERY_ALL_PACKAGES` | Build the installed-app list you see on the selection screen (labels and icons are read locally). |
| `POST_NOTIFICATIONS` | Session countdown, completion results, and warnings if a protection is turned off. |
| Foreground service (special use) | Keeps the blocking service and session countdown alive while enforcing. |
| `SCHEDULE_EXACT_ALARM` | Fires the session-completion alarm at the precise end time (falls back to inexact if denied). |
| `RECEIVE_BOOT_COMPLETED` | Restores an active session after a reboot. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional: improves countdown reliability on aggressive battery managers. |
| Device administrator (optional, no policies) | Adds friction to uninstalling Lockin. It requests no device capabilities (no wipe, no lock, no camera control). |

## Data stored locally (SQLite `lockin.db` + private preferences)

- Packages selected for sessions and their session records (start time, duration,
  deadline, integrity flags, optional commitment text you typed).
- Blocked-attempt counters (package name, count, timestamp, session id).
- Recurring schedules (label, days, time, duration, selected packages).
- Operational metadata: clock observation pair, service heartbeat, deferred actions.
- Private UI preferences: onboarding completion, theme choice.

## What is NOT collected

- No names, contacts, messages, keystrokes, screen contents, location, or device
  identifiers.
- No usage profiles beyond the app's own session/attempt records.
- No crash-reporting SDKs and no third-party SDKs at all.
- No network traffic of any kind (verify it yourself: the merged manifest contains
  no `INTERNET` permission).

## Sharing

Nothing is shared. There is no backend to share with. The only way data leaves the
device is if *you* export it yourself (Lockin ships no automatic export).

## Data deletion

Deleting all data in Settings removes every row above immediately. Uninstalling the
app removes the app's database entirely. There is no remote copy, ever.

## Children

Lockin has no social features and collects no data; it is suitable for personal use
by students. It is not a parental-control product.

## Changes

Any future change that would require network permissions or new data collection will
be reflected in this policy and in the permission list before such behaviour ships.
