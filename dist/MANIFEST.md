# Lockin — Distribution Manifest (v1.1.0)

## Artifacts

| File | Size | SHA-256 |
|---|---|---|
| `Lockin-Android.apk` | 4,895,861 bytes | `8e6894838a54b5cb2afc6619befadcdd518c447d38cd8a45ef32dc641ebde116` |
| `Lockin-source.zip` | 277,944 bytes | `51a7a8dde6940ea4378d45a557728fc3095b6701c80d3b4704f2a3e947b24047` |
| `INSTALL.md` | sideload install guide (Android 6.0+) | — |
| `SHA256SUMS.txt` | checksums for the two binaries | — |

Verify: `sha256sum -c SHA256SUMS.txt`

## Build provenance

- Built: 2026-10-30 (UTC) from the project tree in `Lockin-source.zip`
- Toolchain: JDK 17 (Temurin) · Gradle 8.9 (wrapper included) · AGP 8.7.3 ·
  Kotlin 2.0.21 · Android SDK platform 35 · build-tools 35.0.0
- Variant: `release` (single flavor — no network code anywhere)
- **minSdk 23 (Android 6.0)** · targetSdk/compileSdk 35 · versionName `1.1.0` · versionCode `2`

## Package identity

```
package: name='com.rrrtx.lockin'  versionCode='2'  versionName='1.1.0'
label: 'Lockin'   |   launcher: adaptive hourglass (8.0+) + density PNG fallbacks (6.0–7.1)
```

## Signing

- **Schemes: v1 (JAR) + v2** — verified by `apksigner verify --verbose`
  (`v1: true, v2: true`); v1 is what makes API 23–25 installs possible.
- alias `lockin` · RSA 4096 · CN=Lockin
- Certificate SHA-256: `aa58dfdf50b7ed05d0b3e0aabb81baaaf245d1894c8edcf73e3c8f74b5bf2e2c`
- Certificate SHA-1: `12321820294a77fbe0da1c62acb5a7d21644b5f0`
- Keystore lives **outside** the source tree and is never committed. To update
  the app you must keep this key; for public distribution generate your own and
  replace the fingerprints above.

## Declared permissions (complete merged-manifest list)

| Permission | Purpose |
|---|---|
| `QUERY_ALL_PACKAGES` | installed-app inventory for selection/blocking |
| `POST_NOTIFICATIONS` | countdown, results, protection warnings (runtime request on 13+) |
| `RECEIVE_BOOT_COMPLETED` | session recovery after reboot |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | blocker + countdown services (special-use enforced on 14+) |
| `SCHEDULE_EXACT_ALARM` | precise completion (inexact fallback if denied) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | optional reliability exemption |
| `QUICKBOOT_POWERON` | OEM boot-complete equivalent |

**Not declared: `INTERNET` (verified against the merged manifest), no
`SYSTEM_ALERT_WINDOW`, no NFC, no location/contacts/storage.**
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is platform-added for app-internal
broadcasts.

## Verification commands

```bash
sha256sum -c SHA256SUMS.txt
apksigner verify --verbose Lockin-Android.apk        # expect v1: true, v2: true
aapt dump badging Lockin-Android.apk | grep -E "sdkVersion|versionName"
aapt dump badging Lockin-Android.apk | grep -i internet   # expect nothing
bash scripts/diagnose_install.sh Lockin-Android.apk   # on-device install diagnostics
```

## Corresponding source

`Lockin-source.zip` contains all sources, Gradle wrapper, tests, docs,
branding, `.github/` CI, `LICENSE`, `NOTICE` and `dist/` guides needed to
rebuild (set the four `LOCKIN_*` signing environment variables to sign).
