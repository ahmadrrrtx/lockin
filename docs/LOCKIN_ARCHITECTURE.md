# LOCKIN — Architecture

## 1. Stack and conventions

- Kotlin, classic Views, Fragments, ViewBinding, Material 3 as a base under a custom
  design system (one accent on a monochrome foundation; light + dark).
- Single-activity shell (`MainActivity`) + dedicated `BlockActivity`.
- No Compose, no Navigation component, no annotation processing (no Room/KSP):
  persistence is a hand-written `SQLiteOpenHelper` layer with explicit migrations.

## 2. Processes

| Process | Components | Why |
|---|---|---|
| default | `MainActivity`, all fragments, session authoring | standard UI lifecycle |
| `:lockin_core` | `BlockerAccessibilityService`, `SessionService`, `SessionWatchdogJob`, `SessionAlarmReceiver`, `ScheduleAlarmReceiver`, `BootReceiver`, `BlockActivity` | enforcement survives UI death; crash isolation |

## 3. Persistence (single source of truth)

`lockin.db` (WAL) shared by both processes:

- `sessions` — state (`ACTIVE`/`COMPLETED`), wall/elapsed/boot time triples,
  deadline (+ cumulative wall adjustment), integrity flag, commitment, schedule ref.
- `session_packages` — append-only membership (PK: session+pkg).
- `blocked_attempts` — per-package counts with session linkage.
- `schedules`, `pending_actions` (deferred weakening actions), `meta`
  (last-seen clock pair, service heartbeat, …).

UI-only preferences (onboarding flag, theme) live in `SharedPreferences` in the main
process. Session-critical state never does.

## 4. Session lifecycle

```
UI (Confirm) ──▶ SessionEngine.startSession
                   ├─ transaction: single-active check + insert session + packages
                   ├─ SessionAlarms.scheduleCompletion (exact when permitted)
                   ├─ SessionWatchdogJob.schedule (persisted periodic)
                   ├─ SessionService.start (countdown notification)
                   ├─ DevicePolicyHelper.onSessionStarted (device-owner only)
                   └─ RefreshBus.ping → blocker process reloads cache

BlockerAccessibilityService (per window-state event)
                   ├─ cheap checks: own pkg → protected → in cached set
                   ├─ TimeGuard.evaluate (monotonic baseline while same boot)
                   ├─ not expired → record attempt (throttled) + HOME + BlockActivity
                   └─ expired → SessionEngine.completeIfDue (transition only here)

SessionEngine.completeIfDue (alarm / heartbeat / boot / block screen)
                   ├─ TimeGuard.evaluate again (never trusts the alarm alone)
                   ├─ state-machine legality check
                   ├─ guarded UPDATE … WHERE state='ACTIVE' (single winner)
                   └─ effects: cancel alarm+notification, completion notification,
                      release device-owner blocks, apply deferred actions, ping
```

## 5. Time authority (`TimeGuard`)

1. **Same boot:** deadline = `startElapsed + duration` (elapsedRealtime), immune to
   wall-clock, timezone and DST changes (pure epoch/monotonic math).
2. **Wall jumps observed mid-boot** are measured against the persisted
   `(wall, elapsed)` pair; the stored wall deadline is corrected by exactly the
   measured drift (so a later reboot stays consistent) and jumps > 120 s mark the
   session `DEGRADED`.
3. **After reboot** (boot counter, elapsed regression, or persisted flag): persisted
   wall deadline is authoritative.
4. **Incoherent wall (now < start):** fail-safe re-anchors the *original duration*
   to now — never early, never more than the user committed, flagged `DEGRADED`.
5. Completion always re-runs these rules; an alarm is only a wake-up hint.

## 6. Blocking rules

Decision order on each `TYPE_WINDOW_STATE_CHANGED`:
own package → static/dynamic protected set (systemui, settings, telecom, contacts,
installer, permission controller, launcher, IME, default dialer) → active session →
package membership → TimeGuard. The accessibility config reads no window content
(`canRetrieveWindowContent=false`, window-state events only).

## 7. Resilience inventory

- Foreground `SessionService` (specialUse) with 1 Hz self-terminating tick.
- Watchdog job: persisted, 15 min, re-arms alarms, completes due sessions, warns
  (6 h cooldown) when the accessibility service is off during an enforcing session.
- Heartbeat stamp every 30 s from the blocker process (`meta.service_alive_wall`).
- Boot / package-replaced recovery re-arms schedules + completion alarm.
- Force-stop caveat: the OS cancels alarms/jobs until the app next opens —
  documented as a platform limit; on next open/boot recovery runs immediately.

## 8. Restriction model

There is no in-app setting that weakens an active session (structure beats delay).
The remaining preference-level weakening actions (e.g., releasing device admin) go
through `RestrictionGate`: strengthen → immediate, weaken/unproven while ACTIVE →
queued in `pending_actions`, applied when the session completes.

## 9. Design system

- Colors: warm off-white/near-black surfaces, ink scale, hairline borders, one sand
  accent (`#B7691A` light / `#E8A34B` dark), semantic ok/alert/idle.
- Type: 34/26/21/17/16/14/12 scale via `TextAppearance.Lockin.*`.
- Components: primary/secondary/text buttons, cards (14 dp radius + 1 dp border),
  chips, status dots, section labels, countdown display, empty states — all defined
  in `values/styles.xml` + shape drawables.
- Brand: original geometric hourglass (caps + concave glass + sand mound), adaptive
  icon with monochrome layer, splash identity, white notification silhouette.
