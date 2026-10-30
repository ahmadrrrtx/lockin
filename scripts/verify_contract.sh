#!/usr/bin/env bash
# Static contract checks for the Lockin release build.
# Usage: scripts/verify_contract.sh <path-to-merged-manifest-or-aapt-dir>
set -euo pipefail

cd "$(dirname "$0")/.."
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/tools/android-sdk}}"
FAIL=0

echo "== 1. No INTERNET permission in merged manifest =="
MERGED="app/build/intermediates/merged_manifests/release/AndroidManifest.xml"
if [ ! -f "$MERGED" ]; then
  MERGED=$(find app/build/intermediates -name AndroidManifest.xml -path "*release*" | head -1)
fi
if [ -z "${MERGED:-}" ] || [ ! -f "$MERGED" ]; then
  echo "merged manifest not found"; FAIL=1
else
  if grep -q 'android.permission.INTERNET' "$MERGED"; then
    echo "FAIL: INTERNET permission present"; FAIL=1
  else
    echo "OK: no INTERNET permission ($MERGED)"
  fi
  echo "-- declared permissions --"
  grep -o 'android:name="android.permission[^"]*"' "$MERGED" | sort -u
fi

echo "== 2. No legacy identity strings in sources/resources =="
LEGACY_PATTERN='curbox|neth\.iecal|digipauwse|curbox-api|curbox\.app'
if grep -riE "$LEGACY_PATTERN" app/src build.gradle.kts settings.gradle.kts README.md docs branding 2>/dev/null | grep -v 'scripts/verify_contract.sh'; then
  echo "FAIL: legacy references found"; FAIL=1
else
  echo "OK: no legacy identity strings"
fi

echo "== 3. No early-unlock surface in engine code =="
# The forbidden transition events may only appear inside SessionState.kt and tests.
STRAY=$(grep -rlE 'UnlockEarly|CancelSession|PauseSession|ShortenSession' \
  app/src/main/java --include='*.kt' | grep -v 'core/SessionState.kt' || true)
if [ -n "$STRAY" ]; then
  echo "FAIL: forbidden event types referenced outside the state machine:"; echo "$STRAY"; FAIL=1
else
  echo "OK: forbidden transitions only defined in the state machine"
fi

echo "== 4. Notification builders expose no action buttons =="
if grep -nE 'addAction\(' app/src/main/java/com/rrrtx/lockin/core/Notifications.kt; then
  echo "FAIL: notification action found"; FAIL=1
else
  echo "OK: notifications have no action buttons"
fi

echo "== 5. Package identity / SDK floor / signature =="
APK_REL=app/build/outputs/apk/release/app-release.apk
if [ -f "$APK_REL" ]; then
  AAPT=$(find "$ANDROID_HOME/build-tools" -name aapt 2>/dev/null | sort | tail -1)
  [ -n "$AAPT" ] || { echo "aapt not found in $ANDROID_HOME"; exit 1; }
  BADGING=$("$AAPT" dump badging "$APK_REL")
  echo "$BADGING" | grep "^package:"
  echo "$BADGING" | grep -q "package: name='com.rrrtx.lockin'" || { echo "FAIL: package id"; FAIL=1; }
  echo "$BADGING" | grep -q "versionName='1.1.0'" || { echo "FAIL: versionName must be 1.1.0"; FAIL=1; }
  echo "$BADGING" | grep -q "versionCode='2'" || { echo "FAIL: versionCode must be 2"; FAIL=1; }
  echo "$BADGING" | grep -q "sdkVersion:'23'" || { echo "FAIL: minSdk must be 23 (Android 6.0)"; FAIL=1; }
  echo "$BADGING" | grep -q "targetSdkVersion:'35'" || { echo "FAIL: targetSdk must be 35"; FAIL=1; }
  APKSIGNER=$(find "$ANDROID_HOME/build-tools" -name apksigner 2>/dev/null | sort | tail -1)
  if [ -n "$APKSIGNER" ]; then
    SIG=$("$APKSIGNER" verify --verbose "$APK_REL" 2>/dev/null)
    echo "$SIG" | grep -q "Verified using v1 scheme (JAR signing): true" \
      || { echo "FAIL: v1 (JAR) signature required for API 23-25 installs"; FAIL=1; }
    echo "$SIG" | grep -q "Verified using v2 scheme (APK Signature Scheme v2): true" \
      || { echo "FAIL: v2 signature required"; FAIL=1; }
    [ "$FAIL" -eq 0 ] && echo "OK: identity 1.1.0/2, minSdk 23, targetSdk 35, signatures v1+v2"
  else
    echo "WARN: apksigner not found — signature schemes not verified"
  fi
else
  echo "release apk not present yet"
fi

echo "== 6. Accessibility config minimization =="
grep -q 'canRetrieveWindowContent="false"' app/src/main/res/xml/blocker_accessibility_config.xml \
  && grep -q 'typeWindowStateChanged' app/src/main/res/xml/blocker_accessibility_config.xml \
  && echo "OK: window-state only, no content retrieval" \
  || { echo "FAIL: a11y config too broad"; FAIL=1; }

if [ "$FAIL" -eq 0 ]; then echo "ALL CONTRACT CHECKS PASSED"; else echo "CONTRACT CHECKS FAILED"; exit 1; fi
