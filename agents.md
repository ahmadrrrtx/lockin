# Lockin agent guide

Lockin is a strict focus-commitment Android app: the user selects installed
apps and a duration, commits, and while a session is ACTIVE there is no cancel,
no pause, no shorten, and no early unlock of any kind.

This file applies to the whole repository. Preserve more specific instructions
if a nested `AGENTS.md` is added later.

## Start here

- Single module: `:app`. Do not add new modules without discussion.
- Language and UI: Kotlin, classic Views, Fragments, ViewBinding. Do not add
  Jetpack Compose or the Navigation component.
- JVM target: 17. compileSdk/targetSdk 35, **minSdk 23** (Android 6.0).
- Package root: `app/src/main/java/com/rrrtx/lockin`.
- Prefer the smallest change that follows the nearest existing implementation.
- Readability is more important than cleverness. Follow the style of each file
  you touch.
- Ask before making an architectural change or touching the session schema.
- Comments only where they explain behavior the code itself cannot express.
- User-facing strings: English only (`resourceConfigurations = ["en"]`).

## Build and verify

```bash
./gradlew test lint assembleDebug      # the standard gate
./gradlew assembleRelease              # needs LOCKIN_* signing env vars (see dist/MANIFEST.md)
bash scripts/verify_contract.sh        # product contract (offline, no-unlock, branding)
bash scripts/diagnose_install.sh <apk> # on-device install diagnostics (needs adb)
```

- Keep **lint `NewApi` at 0 errors** — that is the API-23 floor guarantee.
- Unit tests are pure JVM (no Robolectric): keep engine logic in `core/`
  behind injected clocks so it stays testable.

## Working sequence

1. Read the affected class and the closest similar feature before editing.
2. Trace process, lifecycle, and persistence boundaries (there are two
   processes — see below).
3. Make the narrowest complete change. Reuse current patterns and dependencies.
4. Check every entry point: UI, service, receivers, watchdog, alarms, boot.
5. Run the most focused useful verification, then `test` + `lint` +
   `verify_contract.sh` for anything behavioral.
6. Report what changed, which checks ran, and which could not run.

## Nonnegotiable invariants

### Strict session state machine

`core/SessionState.kt` is the single source of truth for allowed transitions
(`IDLE → PREPARING → ACTIVE → COMPLETED`).

- **Never add** a cancel, pause, shorten, skip, or early-unlock transition,
  event, notification action, broadcast extra, or deep-link route that ends an
  ACTIVE session. Contract check 3 enforces this mechanically.
- The only legitimate exit from ACTIVE is `SessionEngine.completeIfDue()`
  when the deadline has passed (plus `TimeGuard` corrections, which may only
  shorten or extend honestly toward the persisted deadline — never "reset" it).
- Adding a "just this once" escape hatch is a product-breaking change, even
  well-intentioned. Discuss it as an issue first.

### Multiple processes

The UI runs in the default process; the blocker service, session service,
watchdog job, receivers, and BlockActivity run in `:lockin_core`.

- SQLite (WAL) is the cross-process truth — access it through
  `SessionRepository` / `LockinDatabase`, never through ad-hoc helpers.
- Do not assume UI-process initialization ran in `:lockin_core`, or vice
  versa. `LockinApp.isMainProcess()` exists for this.
- UI refreshes through `RefreshBus`; the service re-evaluates on its own tick.

### Accessibility service safety

`BlockerAccessibilityService` must never crash because one event fails.

- Keep `onAccessibilityEvent` cheap: window-state events only
  (`TYPE_WINDOWStateChanged`), never traverse node trees there.
- The config must stay minimized: `canRetrieveWindowContent="false"`,
  no report contents, timeout ≤ 100 ms (contract check 6).
- Feature failures are contained and logged — they must not take the service
  down, or blocking dies silently on the user's device.

### Offline and privacy contract

- **Never add** `INTERNET`, analytics, crash reporting, accounts, ads, or any
  network client. Contract check 1 greps the merged manifest.
- No new permissions without an issue discussing the user-visible tradeoff.

### Notifications

- Notifications may inform (countdown, completion, protection warnings) but
  must never act on a session. **No `addAction`** — contract check 4.

### Version awareness (minSdk 23)

- Any API ≥ 24 needs an `SDK_INT` guard or an androidx compat call. Lint's
  `NewApi` is the gate; do not suppress it to go green.
- Notification channels: guard `≥ O`. `startForegroundService`: use
  `ContextCompat`. FGS type: pass `specialUse` only `≥ 34`.
- Keep both **v1 and v2** release signatures — v1 is what installs on
  Android 6/7.

### Honesty

- Never claim Device Owner or uninstall-proof powers Lockin does not have.
- Keep `docs/LOCKIN_THREAT_MODEL.md`, `docs/ANDROID_COMPATIBILITY.md`, and
  in-app copy truthful when behavior changes. "Not tested" must stay marked
  as not tested.

## Architecture map

| Area | Location | Role |
| --- | --- | --- |
| State machine & models | `core/SessionState.kt`, `SessionModels.kt` | allowed transitions, pure logic |
| Engine | `core/SessionEngine.kt` | start/evaluate/completeIfDue/recover; persists TimeGuard corrections |
| Time integrity | `core/TimeGuard.kt` | elapsed-realtime primary, wall deadline, DEGRADED flag |
| Storage | `core/LockinDatabase.kt`, `SessionRepository.kt` | SQLite WAL, append-only sessions |
| Scheduling | `core/SchedulePlanner.kt`, `SessionAlarms.kt`, `*Receiver.kt` | presets → schedules → exact alarms with inexact fallback |
| Blocking | `protection/`, `BlockerAccessibilityService`, `ui/BlockActivity.kt` | allowlists, detection cache (5 s), HOME + 600 ms gate |
| Recovery | `SessionWatchdogJob` (15 min), `BootReceiver`, heartbeat (30 s) | process death, reboot, OEM killers |
| UI shell | `ui/MainActivity.kt` + fragments | single container, `EXTRA_ROUTE` ∈ {active, protection, history} |
| Version seams | `Notifications.kt`, `SessionService.kt`, `LockinApp.kt` | channels, FGS start/type, process name |

## Build variants

- `release`: application id `com.rrrtx.lockin`, signed only when
  `LOCKIN_*` env vars (or `./release.keystore`) are present — CI must stay
  unsigned-capable.
- `debug`: application id suffix `.debug`, label "Lockin Debug" — debug builds
  can never collide with a release install (different package).
