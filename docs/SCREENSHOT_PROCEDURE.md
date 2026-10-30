# Lockin — Screenshot & Runtime Verification Procedure

No emulator or device exists on the build host (no KVM), so this repository ships
**no screenshots at all** rather than fake ones. Run the following on any machine
with a device/emulator to capture the real set.

## 0. Setup

```bash
# Device with Android 6.0+ connected (adb devices shows it)
./gradlew installDebug
adb shell am start -n com.rrrtx.lockin.debug/com.rrrtx.lockin.ui.MainActivity
```

Grant notification permission when prompted; enable the accessibility service
**Settings → Apps → Lockin Debug → Accessibility → Lockin Focus Blocker → On**
(on Android 13+ sideloaded builds first allow: **Special app access → Restricted
settings → Lockin Debug → Allow**).

## 1. Screenshot capture (13 shots)

```bash
mkdir -p docs/screenshots
shot() { adb exec-out screencap -p > "docs/screenshots/$1.png"; sleep 1; }

shot 01-onboarding            # first launch, step 1
# tap through steps 2-3 (permissions visible), then Get started
shot 02-permissions-step
shot 03-home-idle              # home dashboard with Start button
# tap "Start a focus session"
shot 04-app-selection          # multi-select a few apps
# Continue
shot 05-duration               # presets + custom panel visible
# Continue
shot 06-commitment             # confirmation with warning + checkbox
# check the box, press START
shot 07-active-session         # live countdown screen
# press home, open a blocked app from the launcher
shot 08-block-screen           # block screen with countdown
# reopen Lockin → History tab (after completing a session, or during: History while active)
shot 09-history                # history + stats (empty state first, populated later)
shot 10-schedules              # schedules list (+ editor open for 10b)
shot 11-settings               # settings screen
shot 12-protection-center      # protection center statuses
# theme: Settings → Appearance → Dark, revisit Home + Active
shot 13-dark-theme
```

Rename/curate as needed; all files land in `docs/screenshots/`.

## 2. Manual runtime test script (matches `docs/TESTING.md` §4)

1. **Selection:** protected rows (Phone, Settings, launcher) show explanations and
   cannot be checked; search filters; count updates.
2. **Start:** 15-minute session; confirm end time matches now+15 min exactly.
3. **Blocking:** open each selected app → HOME press + block screen; attempt counter
   in History increments (throttled per 5 s).
4. **No escape:** verify no cancel/pause/unlock exists on Active screen, in the
   notification (tap opens Active only), or anywhere in Settings during the session.
5. **Strengthen-only:** “Add more apps” works; no remove control exists.
6. **Process death:** `adb shell am kill com.rrrtx.lockin.debug` (swipe-dismiss UI
   first) → open a blocked app → blocking still enforced from `:lockin_core`.
7. **Reboot:** `adb reboot` mid-session → after boot the countdown persists and the
   completion alarm is re-armed (verify via `adb shell dumpsys alarm | grep lockin`).
8. **Clock tamper:** `adb shell su ... date` unavailable on production builds — use
   Settings → System → Date & time auto-off, set clock +1 h then −1 h mid-session;
   countdown must not jump (monotonic), integrity flag appears for big jumps.
9. **Completion:** at deadline → completion notification, apps open again, history
   row with attempts.
10. **Protection honesty:** turn the accessibility service off mid-session →
    watchdog warning appears within the watchdog window; Protection Center shows
    Off immediately.

## 3. Release APK smoke test

```bash
adb install -r dist/Lockin-Android.apk
adb shell pm dump com.rrrtx.lockin | head -5    # package installed
# repeat items 1-4 above on the release build
```
