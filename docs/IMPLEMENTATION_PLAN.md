# LOCKIN — Implementation Plan

**Product:** Lockin — strict focus commitment app for Android
**Tagline:** "Choose your focus. Commit to it."
**Package:** `com.rrrtx.lockin` · **Min SDK 26** (Android 8.0, initial plan) — final release floor: **23** (Android 6.0, see docs/ANDROID_COMPATIBILITY.md) · **Compile/Target SDK 35**
**Architecture (mandatory):** Kotlin · classic Android Views · Fragments · ViewBinding · single-activity shell + dedicated block activity · Material theming as a base under a custom design system.

---

## 1. Product understanding (what we are building)

Lockin lets a user pick installed apps, pick a duration, confirm a commitment, and start a
**strict focus session**. While the session is active:

- Selected apps cannot be opened (enforced by an AccessibilityService blocking engine).
- There is **no** cancel, pause, shorten, edit-out, or any early-unlock path — in the UI,
  in notifications, in broadcasts, in deep links, or in the blocker logic.
- The selected-app set can only grow during a session (adding strengthens the restriction).
- The session survives process death, force-stop of the UI, and device reboots.
- Lockin carries honest, strongest-legitimate uninstall friction (Device Admin; optional
  Device Owner enhanced mode only when the device genuinely grants it).
- Emergency calling, telecom, launcher, System UI, package installer, permission controller
  and other essential system functions are never blockable. Lockin is never blockable.

Out of scope for v1 (explicitly): URL/keyword/reel blocking, UI hider, usage analytics,
accounts/sync/network, QR/NFC/math unlock challenges, grayscale/DND aids, widgets, Shizuku.

## 2. Engineering foundation and guiding constraints

The design is informed by a careful audit of an existing open-source focus/blocker app
architecture (documented in section 3). We keep its proven, battle-tested *patterns* —
multi-process accessibility blocker, heartbeat + persisted watchdog, boot recovery,
delayed settings weakening, Device Admin friction — while writing Lockin's codebase as an
**original implementation** with its own structure, names, state model and design language.

Non-negotiable engineering rules carried into Lockin:

1. The accessibility service must never crash because one feature failed: every feature
   call is contained; failures are logged and swallowed; `CancellationException` rethrown.
2. No node-tree traversal in `onAccessibilityEvent` — foreground detection uses window
   state events only; the accessibility config is minimized (`typeWindowStateChanged`,
   `canRetrieveWindowContent=false`, no view-id reporting) — strictly what blocking needs.
3. Multi-process storage: canonical state lives in one SQLite database (WAL) readable and
   safely written across the UI process and the `:lockin_core` process. UI-only prefs use
   `SharedPreferences` inside the main process. Never a second source of truth.
4. Restriction weakening is delayed and verified; strengthening applies immediately.
5. The session engine is the **only** place transitions may occur; every entry point
   (UI, receivers, alarms, service) funnels through it with guarded writes.

## 3. Capability audit → Lockin decisions

| Capability (from the audit) | Decision | Lockin treatment |
|---|---|---|
| Accessibility app blocking (home-press + block screen) | **ADAPT** | Core enforcement engine, rewritten |
| Focus session timers | **ADAPT** | Replaced by strict session state machine |
| Early-end / exitable sessions | **REMOVE** | No cancellation transition exists at all |
| Warning-screen unlock challenges (QR, math, retype, NFC, timed) | **REMOVE** | No challenge exists anywhere |
| Cooldown / "on-open" allowance flows | **REMOVE** | Blocking is continuous until deadline |
| App selection with real icons/labels | **ADAPT** | New picker UI, same capability |
| Session countdown notification | **ADAPT** | Owned by blocker process, no actions |
| Service watchdog (persisted periodic job) | **KEEP pattern** | Rewritten, always scheduled while active |
| Boot / package-replaced recovery | **KEEP pattern** | Re-arm alarms + restore enforcement |
| Heartbeat alive-stamp | **KEEP pattern** | Throttled heartbeat in service |
| Settings change delay + restriction comparison | **ADAPT** | Smaller gated set, conservative comparator |
| Device Admin anti-uninstall friction | **ADAPT** | Honest states, detection, notifications |
| Device Owner `setUninstallBlocked` | **ADAPT** | Gated on verified ownership only |
| Room + DataStore persistence | **ADAPT** | Single SQLite layer (no annotation processing) |
| Recurring schedules | **ADAPT** | Weekly study schedules → auto-start |
| Focus history + blocked attempts | **KEEP** | Sessions + attempts tables |
| App groups | **DEFER** | Flat selected set in v1 |
| Grayscale / auto-DND | **DEFER** | Secondary aids, not v1 |
| Shizuku suspend / self-heal | **DEFER** | Optional power feature |
| URL/keyword/reel/UI-hider/analytics/widgets/NFC/API/sync/accounts | **REMOVE** | Not present in Lockin at all |

## 4. Architecture

### 4.1 Processes

| Process | Components |
|---|---|
| main | `MainActivity` (fragment shell), all UI, session authoring |
| `:lockin_core` | `BlockerAccessibilityService`, `SessionService` (foreground countdown), `SessionWatchdogJob`, `BootReceiver`, `SessionAlarmReceiver`, `ScheduleAlarmReceiver`, `BlockActivity` runs here (same task flows) |

### 4.2 Persistence (single SQLite database, WAL, versioned with explicit migrations)

- `sessions` — id, state (`PREPARING` rows are UI-side only; persisted rows are `ACTIVE`
  or `COMPLETED`), started_at_wall, duration_ms, deadline_wall, start_elapsed,
  start_boot_count, last_seen_wall, last_seen_elapsed, integrity (`OK`/`DEGRADED`),
  commitment_text, completed_at_wall, end_reason.
- `session_packages` — (session_id, pkg): append-only while ACTIVE.
- `blocked_attempts` — pkg, attempt count, last_at, session_id.
- `schedules` — label, days bitmask, hour, minute, duration, packages JSON, enabled.
- `meta` — schema markers, heartbeat stamps.

### 4.3 Session state machine (pure Kotlin, unit-tested)

```
IDLE ──create──▶ PREPARING ──confirm──▶ ACTIVE ──deadline verified──▶ COMPLETED
                    │  ▲
                    │  └── abandon (only before START)
                    ▼
                  IDLE
```

- `ACTIVE → (CANCEL | PAUSE | SHORTEN | UNLOCK)` are **not legal transitions** — the
  transition table simply does not contain them; guarded SQL additionally enforces
  `UPDATE ... WHERE state='ACTIVE'`.
- Completion only through `SessionEngine.completeIfDue()` with verified time.

### 4.4 Time model (TimeGuard)

- While the original boot continues: deadline computed from **elapsedRealtime baseline**
  (`start_elapsed + duration`) — immune to wall-clock changes, timezone and DST (epoch math).
- After reboot: fall back to persisted `deadline_wall`.
- Clock tamper detection: pair `(last_seen_wall, last_seen_elapsed)` detects backward wall
  jumps mid-boot → deadline extended by **exactly the detected delta** (bounded by the
  original duration), integrity flagged `DEGRADED`, surfaced honestly in the UI.
- Post-boot inconsistency (wall now < session start) → fail-safe: hold to the **original
  full duration**, flag `DEGRADED`, explain in UI. Never silently unlock early; never add
  arbitrary extra time.
- Completion alarm: exact alarm when permitted (`SCHEDULE_EXACT_ALARM`), inexact fallback
  otherwise; the blocker re-checks the deadline on every event regardless, so a missed
  alarm can never leak an expired block, and an early/wrong alarm cannot unlock early
  because completion requires TimeGuard verification.

### 4.5 Enforcement (BlockerAccessibilityService, `:lockin_core`)

- Foreground package from `TYPE_WINDOW_STATE_CHANGED`.
- Checks (in order): service self-guard (never block Lockin) → never-block package list
  → active session in cached state → package ∈ immutable set → deadline verified not due
  → record throttled attempt → `GLOBAL_ACTION_HOME` + launch `BlockActivity`.
- State cache reloaded on: service connect, cross-process refresh broadcast, TTL, and on
  every 50th event; always re-verified against DB before blocking.
- Every feature call wrapped; a failure in recording/logic never kills the service.

### 4.6 Resilience

- Foreground `SessionService` (`specialUse`) shows the countdown notification (no actions
  that affect the session; tap opens the Active screen only).
- `SessionWatchdogJob` — persisted periodic job: ensures watchdog + completion alarm exist,
  re-notifies if accessibility is off while a session is ACTIVE.
- `BootReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`) — re-arm alarms, reload state.
- `SessionAlarmReceiver` — completion + watchdog alarm entry point in `:lockin_core`.
- Force-stop caveat documented honestly (OS cancels our jobs until next app open).

### 4.7 Protection model (honest, layered)

1. **In-app strictness** — no UI/route can end a session (state machine).
2. **Device Admin** — optional activation; adds uninstall friction; deactivation attempts
   while ACTIVE are detected and reported (notification + protection center state).
3. **Device Owner enhanced mode** — if (and only if) `isDeviceOwnerApp(pkg)` is true,
   `setUninstallBlocked(true)` applied to Lockin + session packages at start, released at
   completion. Not part of normal onboarding; UI explains provisioning requirement.
4. **Documented unavoidable paths** — a11y toggle in Settings, force-stop, (if device
   owner not granted) package uninstaller — always shown truthfully in Protection Center.

## 5. UX and visual system

- **Brand:** original geometric hourglass mark (vector), monochrome ink + single
  "sand" amber accent; light + dark themes; adaptive launcher icon + monochrome layer;
  splash identity; white silhouette notification icon. `branding/SPEC.md` documents it.
- **Shell:** `MainActivity` + bottom navigation (Home · History · Schedules · Settings)
  with an inner stack for the creation flow: Select apps → Duration → Confirm → Active.
- **Screens:** Onboarding (intro + permission wizard), Home dashboard (primary action),
  Select applications (search, real icons, multi-select, count, protection of critical
  apps explained), Duration (presets 15/25/30/50/60/120/240/480/1440 min + custom, max
  documented 7 days), Commitment confirmation (apps, count, exact end time, protection
  status, warning, optional commitment statement checkbox), Active session (countdown,
  count, protection status, **no interactive escape**), Block screen (informational),
  History (sessions + genuinely recorded attempts), Schedules, Settings, Protection
  Center, About/Privacy.
- Design system: spacing scale, semantic colors, type scale, buttons, rows, chips,
  status dots, sheets, dialogs, countdown component, empty/loading/error states.

## 6. Privacy

Offline-first: **no `INTERNET` permission**, no accounts, no analytics, no network code.
Accessibility config minimized to window-state events; no screen content read or stored.
Local data only: packages in sessions, attempts, schedules, prefs. Data deletion = app
settings clear/uninstall (no server). `docs/PRIVACY_POLICY.md` documents every permission.

## 7. Test strategy

| Level | Tooling | Coverage |
|---|---|---|
| JVM unit | JUnit4 | State machine transitions (incl. all illegal ones), TimeGuard (same-boot, reboot, backward/forward jumps, DST-safe epoch math, degraded fail-safe), protected-package rules, restriction comparator, duration presets/max, schedule math, attempt throttling |
| Build/lint | Gradle + Android Lint | All variants compile, manifest merge audit, no INTERNET in merged manifest |
| Static verification | script | No blockable-path from notification/broadcast/deep-link; no old identity strings |
| Runtime | — | **No emulator/device available in this environment (no KVM).** A precise `docs/SCREENSHOT_PROCEDURE.md` + `docs/TESTING.md` state exactly what was and was not executed. No fabricated screenshots or device results. |

## 8. Phases (execution order)

| Phase | Deliverable | Exit criteria |
|---|---|---|
| **P1 Recon** | Architecture understanding, capability audit (§3) | This plan reviewed against the codebase |
| **P2 Decisions & docs** | `PRODUCT_SPEC`, `ARCHITECTURE`, `THREAT_MODEL` | Docs consistent with plan |
| **P3 Identity** | Hourglass SVG/adaptive icons, themes, `branding/SPEC.md` | Resources compile |
| **P4 Toolchain & scaffold** | Gradle 8.9 + AGP + SDK 35 project, empty shell builds | `assembleDebug` green |
| **P5 Core engine** | SQLite layer, SessionEngine, TimeGuard, schedules + JVM tests | `test` green |
| **P6 Enforcement** | Accessibility service, BlockActivity, SessionService, watchdog, boot/alarm receivers | Code complete + builds |
| **P7 Full UX** | All screens, navigation, design system, light/dark | `assembleDebug` green |
| **P8 Security & privacy** | Device Admin, DO gate, permission audit, privacy doc | Manifest audit passes |
| **P9 Verification** | Unit tests, lint, release build, manifest checks | `test` + `assembleRelease` green |
| **P10 Distribution** | Signed `dist/` artifacts, checksums, INSTALL/MANIFEST, README, screenshot procedure, final audit report | Artifacts verified by hash + aapt |

## 9. Risks and mitigations

- **Low-RAM build environment (2 GB):** small heap Gradle flags, single module, no KSP,
  minimal dependencies, lint kept non-blocking.
- **Exact-alarm denial on some OEMs:** inexact fallback + deadline re-check in service.
- **Force-stop by user:** OS-level; documented; recovery on next open/boot.
- **Clock tampering:** TimeGuard fail-safe + integrity flag (§4.4), tested.
- **No device for runtime tests:** honest reporting, screenshot procedure provided.
