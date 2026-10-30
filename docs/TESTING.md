# Lockin — Testing Record

This document states **exactly** what was executed and what was not. Nothing here is
inferred or simulated.

## Environment

- Build host: Linux x86-64, 2 CPUs, 2 GB RAM (+ 4 GB swap), no KVM, no emulator image.
- JDK 17 (Temurin), Android SDK platform 35, build-tools 35.0.0, Gradle 8.9, AGP 8.7.3.
- No physical Android device and no hardware-accelerated emulator available
  (`/dev/kvm` absent; software emulation is not feasible on this host).

## 1. JVM unit tests — EXECUTED, PASSING

Command: `gradle test` (task `testDebugUnitTest`)

| Suite | Coverage |
|---|---|
| `SessionStateMachineTest` (11) | legal IDLE→PREPARING→ACTIVE→COMPLETED path; ACTIVE rejects cancel, pause, shorten, unlock, remove-package, weaken, create-while-active, abandon, confirm; COMPLETED terminal; AddPackage self-loop; `canTransition` mirror |
| `TimeGuardTest` (13) | monotonic baseline; forward wall jump cannot unlock early; backward jump corrects deadline + DEGRADED >120 s; silent small-step correction; expiry only at exact deadline; boot-counter/elapsed-regression/lastSeen reboot detection; persisted wall mode; sticky DEGRADED; fail-safe re-anchor (never early, ≤ original duration); multi-jump adjustment accumulation |
| `DurationRulesTest` (7) | preset list matches spec; bounds incl. min/max edges; `requireValid` throws; epoch end math; parts/formatShort/formatCountdown |
| `ProtectedRulesTest` (7) | self/systemui/telecom/installer protected; dynamic launcher/IME/dialer; ordinary apps selectable; explanations present; empty package protected |
| `SchedulePlannerTest` (7) | same-day next slot; skip to matching day; strictly-after semantics; every-day mask; week wrap; Sunday bit mapping; invalid inputs → 0 |
| `RestrictionGateTest` (7) | strengthen-immediate, weaken-defer, unproven-defer (conservative), idle-allows, classifier, action gating |

Result: **52 unique tests, 0 failures, 0 errors**, executed in both the debug
and the release unit-test variants (**104 executions** total); see
`app/build/test-results/`).

## 2. Build verification — EXECUTED, PASSING

| Task | Result |
|---|---|
| `assembleDebug` | BUILD SUCCESSFUL |
| `compileDebugKotlin` (full UI + engine) | BUILD SUCCESSFUL |
| `assembleRelease` | BUILD SUCCESSFUL (signed — see `dist/SHA256SUMS.txt`) |
| Android Lint (`gradle lint`) | runs with `abortOnError=false`; report at `app/build/reports/lint-results-*` — warnings triaged below |

## 3. Static contract checks — EXECUTED (scripts/verify_contract.sh)

- Merged release manifest contains **no `INTERNET` permission** (offline claim proven
  against the actual merged manifest, not assumed).
- No legacy identity strings (old product names, old package ids, old domains)
  anywhere in sources/resources/manifest.
- No unlock/challenge surface: source scan confirms zero cancel/pause/unlock APIs
  and notification builders expose no action buttons.
- Package identity: `applicationId com.rrrtx.lockin` verified via `aapt badging`.

## 4. NOT executed (honest gaps)

| Check | Why not | How to run it elsewhere |
|---|---|---|
| Instrumentation / on-device tests | no device, no emulator on this host | `./gradlew connectedDebugAndroidTest` with a device attached |
| Runtime screenshots | same | see `docs/SCREENSHOT_PROCEDURE.md` (exact adb steps) |
| Accessibility blocking walkthrough | same | procedure §3 of the screenshot doc doubles as the manual test script |
| Play-policy review | out of scope (direct distribution first) | — |

Known build-environment caveats: Lint at **minSdk 23** completed with
**0 errors / 100 warnings** — including **0 `NewApi` and 0 `InlinedApi`** (the
API-availability gate for the Android 6.0 floor). Triage:

| Warning class (count) | Disposition |
|---|---|
| UnusedResources (35) | strings/colors/styles kept for future polish or data-driven use; no *missing*-resource errors exist — every reference in code/layouts resolves (AAPT2 + build prove this) |
| HardcodedText (19) | preset chip labels and `tools:text` previews (“15 min”, “0:00”) intentionally numeric |
| Overdraw (14) | intentional explicit backgrounds for deterministic rendering |
| PluralsCandidate (10) | numeric copy like “1 selected apps”; grammar polish queued |
| GradleDependency (8) | newer library versions exist; pinned versions are the verified matrix for this release (offline-reproducible builds) |
| SetTextI18n (5) | `DurationRules` compact forms (“1h 30m”), locale-neutral by design |
| NotifyDataSetChanged (4) | list sizes are ≤ a few hundred rows; negligible |
| DisableBaselineAlignment (3) | minor static-layout optimisation, ignored in these layouts |
| UseCompoundDrawables (1) | style preference only |
| BatteryLife (1) | expected: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is Play-policy-sensitive only on Play; this build is distributed directly (documented in `dist/INSTALL.md`) |

Full report: `app/build/reports/lint-results-debug.txt`.
