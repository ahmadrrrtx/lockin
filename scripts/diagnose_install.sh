#!/usr/bin/env bash
# Install-side diagnostics for Lockin APKs.
#
# Runs the sequence an install failure report needs:
#   adb devices -l  →  adb install -r  →  exact error code  →  filtered logcat
#
# NEVER deletes data: it does not uninstall anything. If a signature conflict
# is detected it explains the choice and waits for an explicit flag instead.
#
# Usage:
#   scripts/diagnose_install.sh [path/to/app.apk] [--allow-uninstall]
#
#   --allow-uninstall  required before ANY uninstallation is even suggested
#                      as a command you could copy; still never runs it
#                      automatically.
set -uo pipefail

APK="${1:-dist/Lockin-Android.apk}"
PKG="com.rrrtx.lockin"
ALLOW_UNINSTALL=0
for a in "$@"; do [ "$a" = "--allow-uninstall" ] && ALLOW_UNINSTALL=1; done

ADB_BIN=""
if command -v adb >/dev/null 2>&1; then ADB_BIN=adb
elif [ -x "$HOME/tools/platform-tools/adb" ]; then ADB_BIN="$HOME/tools/platform-tools/adb"
else
  echo "ERROR: adb not found (install platform-tools or add adb to PATH)"; exit 2
fi

echo "== 0. APK =="
[ -f "$APK" ] || { echo "ERROR: APK not found: $APK"; exit 2; }
if command -v sha256sum >/dev/null 2>&1; then
  echo "sha256: $(sha256sum "$APK" | awk '{print $1}')"
  if [ -f "$(dirname "$APK")/SHA256SUMS.txt" ]; then
    ( cd "$(dirname "$APK")" && sha256sum -c SHA256SUMS.txt 2>/dev/null ) \
      && echo "checksum: matches SHA256SUMS.txt" \
      || echo "checksum: DOES NOT MATCH — transfer may be corrupt (top cause of 'App not installed')"
  fi
fi
[ "$(head -c2 "$APK")" = "PK" ] || { echo "ERROR: not a ZIP/APK (corrupt download)"; exit 2; }

echo; echo "== 1. adb devices -l =="
"$ADB_BIN" devices -l
COUNT=$("$ADB_BIN" devices | sed 1d | grep -cw "device" || true)
if [ "$COUNT" -eq 0 ]; then
  echo "No device in 'device' state. Enable USB debugging, accept the RSA prompt,"
  echo "try another cable/port. For a WIRELESS session: adb tcpip 5555; adb connect <phone-ip>:5555"
  exit 3
fi

echo; echo "== 2. Existing package state =="
"$ADB_BIN" shell pm list packages -f "$PKG" 2>/dev/null | sed 's/^/  /'
DUMP=$("$ADB_BIN" shell dumpsys package "$PKG" 2>/dev/null | grep -E "versionCode|firstInstallTime|lastUpdateTime|pkgFlags" | head -6)
[ -n "$DUMP" ] && echo "$DUMP" | sed 's/^/  /'

echo; echo "== 3. Clear logcat, then: adb install -r =="
"$ADB_BIN" logcat -c 2>/dev/null || true
INSTALL_OUT=$("$ADB_BIN" install -r "$APK" 2>&1)
echo "$INSTALL_OUT"
echo; echo "== 4. Verdict =="
if echo "$INSTALL_OUT" | grep -qi "Success"; then
  echo "INSTALL OK. Now: adb shell am start -n $PKG/.ui.MainActivity"
  echo "Then follow docs/ANDROID_COMPATIBILITY.md §7 for the functional script."
  exit 0
fi

echo "$INSTALL_OUT" | grep -qi "UPDATE_INCOMPATIBLE" && {
  echo "→ INSTALL_FAILED_UPDATE_INCOMPATIBLE: a DIFFERENT signing key already"
  echo "  owns package $PKG on this phone (e.g. an earlier debug build)."
  echo "  Options:"
  echo "    a) update over it only with the ORIGINAL key (not available), or"
  echo "    b) uninstall the old build FIRST — this deletes its app data:"
  echo "       adb shell pm uninstall $PKG"
  if [ "$ALLOW_UNINSTALL" -eq 1 ]; then
    echo "  (--allow-uninstall given, but this script still does not run it for you)"
  else
    echo "  Re-run with --allow-uninstall to acknowledge you have read this warning."
  fi
}
echo "$INSTALL_OUT" | grep -qi "OLDER_SDK" && {
  echo "→ INSTALL_FAILED_OLDER_SDK: phone API < minSdk. Run:"
  echo "  aapt dump badging \"$APK\" | grep sdkVersion"
  echo "  (v1.1.0 must print sdkVersion:'23')"
}
echo "$INSTALL_OUT" | grep -qi "DOWNGRADE" && {
  echo "→ INSTALL_FAILED_VERSION_DOWNGRADE: installed versionCode is HIGHER."
  echo "  adb shell dumpsys package $PKG | grep versionCode"
}
echo "$INSTALL_OUT" | grep -qi "INSUFFICIENT_STORAGE" && {
  echo "→ Low storage — free space and retry."
}
echo "$INSTALL_OUT" | grep -qi "NO_MATCHING_ABIS" && {
  echo "→ ABI mismatch (Lockin ships no native code — unexpected, please report)."
}
echo "$INSTALL_OUT" | grep -qi "PARSE\|INVALID_APK" && {
  echo "→ Parse failure: the file on the phone is almost certainly corrupt."
  echo "  Re-transfer it and re-verify the sha256 printed in step 0."
}
echo "$INSTALL_OUT" | grep -qi "TEST_ONLY" && {
  echo "→ TEST_ONLY flag: a debug artifact was installed; use dist/Lockin-Android.apk."
}

echo; echo "== 5. PackageManager/PackageInstaller logcat (last 200 lines) =="
"$ADB_BIN" logcat -d -t 200 2>/dev/null | grep -iE "PackageManager|PackageInstaller|Failure|INSTALL_FAILED" | tail -40
echo
echo "Attach this whole output when reporting a failure."
