#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

find_android_sdk() {
  if [[ -n "${ANDROID_SDK_ROOT:-}" && -d "${ANDROID_SDK_ROOT}" ]]; then
    echo "${ANDROID_SDK_ROOT}"
    return 0
  fi

  if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}" ]]; then
    echo "${ANDROID_HOME}"
    return 0
  fi

  local mac_default="$HOME/Library/Android/sdk"
  if [[ -d "$mac_default" ]]; then
    echo "$mac_default"
    return 0
  fi

  return 1
}

find_adb() {
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return 0
  fi

  if [[ -n "${ANDROID_SDK_ROOT:-}" && -x "${ANDROID_SDK_ROOT}/platform-tools/adb" ]]; then
    echo "${ANDROID_SDK_ROOT}/platform-tools/adb"
    return 0
  fi

  if [[ -n "${ANDROID_HOME:-}" && -x "${ANDROID_HOME}/platform-tools/adb" ]]; then
    echo "${ANDROID_HOME}/platform-tools/adb"
    return 0
  fi

  local default_adb="$HOME/Library/Android/sdk/platform-tools/adb"
  if [[ -x "$default_adb" ]]; then
    echo "$default_adb"
    return 0
  fi

  return 1
}

current_java_major() {
  local v
  v="$(java -version 2>&1 | awk -F '"' '/version/ {print $2; exit}')"
  if [[ "$v" =~ ^1\.([0-9]+) ]]; then
    echo "${BASH_REMATCH[1]}"
  else
    echo "$v" | awk -F. '{print $1}'
  fi
}

java_major_for_home() {
  local home="$1"
  "$home/bin/java" -version 2>&1 | awk -F '"' '/version/ {print $2; exit}' | awk -F. '{print $1}'
}

if [[ -x "./gradlew" ]]; then
  GRADLE_CMD="./gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD="gradle"
else
  echo "❌ Gradle not found. Install Gradle or generate ./gradlew first."
  exit 1
fi

if command -v java >/dev/null 2>&1; then
  JAVA_MAJOR="$(current_java_major || echo 0)"
  if [[ "${JAVA_MAJOR:-0}" -ge 21 ]]; then
    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
      JAVA17_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
      if [[ -n "$JAVA17_HOME" ]]; then
        JAVA17_MAJOR="$(java_major_for_home "$JAVA17_HOME" || echo 0)"
        if [[ "$JAVA17_MAJOR" == "17" ]]; then
          export JAVA_HOME="$JAVA17_HOME"
          export PATH="$JAVA_HOME/bin:$PATH"
          echo "ℹ Using Java 17 for Gradle: $JAVA_HOME"
        else
          echo "⚠ Java 17 not found. Build may show Kotlin daemon warnings on Java $JAVA_MAJOR."
        fi
      else
        echo "⚠ Java 17 not found. Build may show Kotlin daemon warnings on Java $JAVA_MAJOR."
      fi
    fi
  fi
fi

SDK_PATH="$(find_android_sdk || true)"
if [[ -n "$SDK_PATH" ]]; then
  if [[ ! -f "$PROJECT_DIR/local.properties" ]]; then
    # Write the path verbatim. The previous substitution inserted stray backslashes,
    # producing sdk.dir=\/Users\/... which Android Lint rejects as PropertyEscape.
    echo "sdk.dir=${SDK_PATH}" > "$PROJECT_DIR/local.properties"
    echo "ℹ Created local.properties with sdk.dir=$SDK_PATH"
  fi
else
  echo "⚠ Android SDK not auto-detected."
  echo "   Install Android SDK via Android Studio, or set ANDROID_SDK_ROOT."
fi

echo "▶ Building debug APK..."
"$GRADLE_CMD" --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:assembleDebug

APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK_PATH" ]]; then
  echo "❌ APK not found at: $APK_PATH"
  exit 1
fi

ADB_BIN="$(find_adb || true)"
if [[ -z "$ADB_BIN" ]]; then
  echo "⚠ adb not found. APK built successfully at: $APK_PATH"
  echo "   Install manually from Android Studio or set ANDROID_SDK_ROOT."
  exit 0
fi

echo "▶ Checking connected devices..."
DEVICE_COUNT="$($ADB_BIN devices | awk 'NR>1 && $2=="device" {count++} END{print count+0}')"

if [[ "$DEVICE_COUNT" -eq 0 ]]; then
  echo "⚠ No connected/authorized Android devices found."
  echo "   APK built at: $APK_PATH"
  exit 0
fi

echo "▶ Installing on connected device..."
TARGET_SERIAL="${ANDROID_SERIAL:-}"

if [[ -z "$TARGET_SERIAL" ]]; then
  TARGET_SERIAL="$($ADB_BIN devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
  if [[ "$DEVICE_COUNT" -gt 1 ]]; then
    echo "ℹ Multiple devices detected. Auto-selecting first: $TARGET_SERIAL"
    echo "   Set ANDROID_SERIAL to choose a specific device."
  fi
fi

$ADB_BIN -s "$TARGET_SERIAL" install -r "$APK_PATH"

echo "✅ Build + install complete."
echo "   Package: com.deox9.musicplayer"
echo "   Device: $TARGET_SERIAL"
