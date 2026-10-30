# LOCKIN — Product Spec

**Tagline:** Choose your focus. Commit to it.
**Package:** `com.rrrtx.lockin` · minSdk 23 · targetSdk 35 · offline-only (no INTERNET permission)

## 1. Vision

Lockin is a focus *commitment* product for students and anyone who wants real control
over phone distraction. The user picks installed apps, picks a duration, confirms a
commitment, and starts a **strict focus session**. While it runs:

- the selected apps cannot be opened,
- there is no cancel, pause, shorten, or early unlock — anywhere,
- the selected set cannot be edited down (it can only grow),
- the session survives process death and device reboots,
- impulsive escape attempts are hard and deliberate, and Lockin reports honestly
  about the limits Android imposes on any app.

Experience statement: *"I choose the apps. I decide how long. Once I commit, I cannot
casually change my mind."*

## 2. Session model

State machine (single legal path, enforced in code + guarded SQL):

```
IDLE ──create──▶ PREPARING ──confirm──▶ ACTIVE ──verified deadline──▶ COMPLETED
                     │  ▲
                     └──┘ abandon (before START only)
```

Active-session invariants:

| Invariant | Enforcement point |
|---|---|
| No cancel/pause/shorten/unlock event | `SessionMachine` rejects; no code path emits |
| Completion only at verified deadline | `TimeGuard` + `SessionEngine.completeIfDue` guarded `UPDATE … WHERE state='ACTIVE'` |
| Selected set append-only | `SessionRepository.addPackages` only; no delete API for sessions |
| No notification action affects session | notification builders expose content intent only |
| No broadcast/deep-link ends session | receivers only call `completeIfDue` (time-verified) |
| Strictness not weakenable from any screen | `RestrictionGate` defers weakening actions to session end |
| Blocked set immutable for enforcement | cached set + DB reload; single-active invariant in transaction |

## 3. Screens

1. **Onboarding** — intro (3 steps) + permission wizard (accessibility, notifications)
   with live status dots and skip.
2. **Home** — wordmark, tagline, active-session card (live countdown) or idle state,
   primary action *Start a focus session*, stats (focused today / sessions / attempts),
   degraded-protection banner.
3. **Select applications** — real installed inventory with genuine icons and labels,
   instant search, multi-select, protected rows shown with explanations (never hidden),
   running selected count, ≥1 required to continue.
4. **Choose duration** — presets 15/25/30/50/60/120/240/480/1440 min + custom
   (min 5 min, max **7 days**, documented), live exact expected end time.
5. **Confirm commitment** — app chips + count, duration, exact end time, live
   protection status, explicit warning, required acknowledgment, optional personal
   commitment statement, START.
6. **Active session** — big countdown, end time, locked-app count, protection dot,
   commitment display, integrity warning when degraded, *add more apps* (only
   strengthening is possible), no exit controls of any kind.
7. **Block screen** — shown when a locked app is opened: locked app identity,
   countdown, end time, app count, honest no-early-unlock note; single action opens
   Lockin's session view.
8. **History** — completed sessions with date, range, duration, app count, genuinely
   recorded blocked-attempt counts; totals header; empty state.
9. **Schedules** — recurring weekly plans (days/time/duration/apps/enabled) that can
   only *start* sessions while idle; editor with time picker, duration chips, day
   toggles, app picker; next-run display.
10. **Settings** — permissions rows with status dots (accessibility, notifications,
    battery exemption, device admin), protection center link, theme (system/light/
    dark), privacy policy, delete-all-local-data, about + version.
11. **Protection center** — live status for blocker service, device admin, enhanced
    device-owner mode (honest "not available" unless genuinely provisioned), battery
    exemption, exact alarm permission, time integrity; plus a permanent
    *"What Android still allows"* disclosure.

## 4. What is intentionally absent

URL/keyword/reels/UI-hider blocking, usage analytics, accounts/sync/network,
challenge-based unlock flows (QR, NFC, math, retyping), cooldowns, "exitable"
sessions, widgets, Shizuku/root features, grayscale/DND (deferred), advertising.

## 5. Platform honesty

- Ordinary personal phones: in-app strictness + device-admin uninstall friction;
  a determined user can still disable the accessibility service, force-stop, or
  remove admin then uninstall. Stated in the UI.
- `setUninstallBlocked` is used only when the platform verifiably reports Lockin as
  device owner; normal onboarding never implies otherwise.
- Emergency calling, telecom, launcher, System UI, package installer, permission
  controller, keyboards and Lockin itself are never blockable (explained per row).
- No claim of "unbeatable" protection appears anywhere.
