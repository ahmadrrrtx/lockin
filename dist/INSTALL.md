# Installing Lockin — step by step (no computer knowledge needed)

**What you need:** an Android phone (Android 6.0 / API 23 or newer) and the file
`Lockin-Android.apk` from this folder.

## 0. "App not installed."? → Google Play Protect blocked it (read first)

If you saw **"App blocked to protect your device — This app can request access
to sensitive data. This can increase the risk of identity theft or financial
fraud"** (OK button only), or the install simply ended with **"App not
installed."**, this is **Google Play Protect's automatic block — not a broken
APK**.

**Why (Google's own policy):** apps downloaded from *internet-sideloading
sources* — web browsers, messaging apps, file managers — that declare
**ACCESSIBILITY**, SMS, or notification-listener permissions are blocked at
install time ([official Google guidance](https://developers.google.com/android/play-protect/warning-dev-guidance)).
Lockin's core is an accessibility service (it only ever sees *which app* is in
front of you — never your screen content), so **downloading via a browser
triggers this on most current phones**. The OK-only dialog is intentional:
there is no "Install anyway" button on it.

Pick ONE route:

**Route A — pause Play Protect for one install (fastest, no computer)** ⭐
1. Open **Google Play Store** → tap your **profile icon** (top-right).
2. Tap **Play Protect** → **⚙️ gear** → turn **OFF** "Scan apps with Play Protect".
3. Install the APK normally (Downloads/Files → `Lockin-Android.apk`).
4. Turn scanning **back on** right away.

**Route B — Google's "allow unverified packages" flow (no computer, one-time 24-hour wait)**
Devices that offer it (newer Android): Settings → About phone → tap **Build
number** 7× → Settings → System → **Developer options** → **Allow unverified
packages / Apps from Unverified Developers** → confirm → **reboot** → wait
**24 hours** → return to the toggle → allow (7 days or indefinitely). The
installer then offers **Install anyway**.

**Route C — computer + adb (unrestricted; Google's developer route)**
Enable **USB debugging**, connect the phone, run:

```bash
adb install Lockin-Android.apk
```

adb installs are not subject to this Play Protect block.

**Route D — store installs**
APKs installed through a store client (session-based installs — e.g. F-Droid,
once published) don't use the browser-sideload path.

**After any successful install — Android 13+ only:** Settings → Apps → Lockin
→ **⋮ → Allow restricted settings**, *then* enable the accessibility service
in onboarding (otherwise that toggle looks greyed out).

**Source & integrity:** download only from the
[official releases](https://github.com/ahmadrrrtx/lockin/releases/latest) or
the project website. Copies re-shared through Google Drive or messaging
(`Lockin-Android (1).apk`) can be stale or altered — verify `SHA-256` against
`SHA256SUMS.txt` (v1.1.0 = `8e689483…bde116`).

## 1. Put the APK on your phone

Any way you like: USB cable, messaging app to yourself, email-to-yourself,
Google Drive/Dropbox, or a file manager with nearby-share.

## 2. Install it

1. Open the APK file on your phone (Files → Downloads → `Lockin-Android.apk`).
2. Android will warn: **"For your security, your phone is not allowed to install
   unknown apps from this source."** This is normal for apps installed outside the
   Play Store. Tap **Settings** → turn on **Allow from this source** → go back.
3. Tap **Install**. When you see **App installed**, tap **Open**.

## 3. First-run setup (about 2 minutes)

Lockin walks you through everything, but here is what happens:

1. **Welcome screens** — what Lockin does. Tap *Continue*.
2. **Enable the blocker** — Lockin opens Android’s *Accessibility* settings.
   Find **Lockin Focus Blocker** → turn it **On** → confirm the warning
   (the service only sees *which app* is in front of you — it never reads your
   screen content; see `docs/PRIVACY_POLICY.md`).
   - *Android 13+ extra step for sideloaded apps:* if the toggle looks disabled
     (“restricted settings”), go to
     **Settings → Apps → Lockin → Special app access → Restricted settings →
     Allow**, then return and enable the service.
3. **Notifications** — allow them so the countdown and protection warnings show.
4. **Done** → the Home screen. Tap **Start a focus session**.

## 4. Start your first session

1. Search/select the apps you want locked → **Continue**.
2. Pick a duration (or Custom, up to 7 days) → **Continue**.
3. Read the commitment screen (it shows the exact end time) → tick
   *I understand and I commit* → **START**.
4. When you try to open a locked app, Lockin shows the block screen with the
   remaining time. There is intentionally no unlock button.

## 5. Optional protection (recommended)

**Settings → Device admin protection → Activate.** This makes Lockin harder to
uninstall (Android requires deactivating admin first on most phones). Lockin never
claims this is impossible to defeat — see **Protection center** for the honest list.

Also recommended in Settings:
- **Battery optimization exemption** (reliable countdown on strict battery managers)
- **Exact alarm permission** (precision end time on Android 13+)

## 6. Removing Lockin

If a session is active, finish it first (or wait for it to end). Then:
Settings → Device admin protection → deactivate (if you enabled it) → uninstall
the app normally. Uninstalling deletes all local data — there is no cloud copy.

## Troubleshooting

| Problem | Fix |
|---|---|
| **“App not installed.” / “App blocked to protect your device”** | Play Protect auto-block — use Route A/B/C in **§0** above |
| Locked apps open anyway | Settings → Apps → Lockin → Accessibility → confirm *Lockin Focus Blocker* is On; check Protection center |
| No countdown notification | Allow notifications for Lockin (long-press the app icon → App info → Notifications) |
| Session ended while phone was off | Normal — Lockin verifies the real clock on boot and completes correctly |
| Play Protect blocks the install | Choose **Install anyway** only if you downloaded the APK from the official source and verified its SHA-256 against `SHA256SUMS.txt` |

## Verify your copy (optional but smart)

On a computer:

```bash
sha256sum Lockin-Android.apk
```

Compare with `SHA256SUMS.txt` in this folder.
