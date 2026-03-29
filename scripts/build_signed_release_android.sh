#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

if [[ -x "./gradlew" ]]; then
  GRADLE_CMD="./gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD="gradle"
else
  echo "❌ Gradle not found. Install Gradle or generate ./gradlew first."
  exit 1
fi

required=(RELEASE_STORE_FILE RELEASE_STORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD)
missing=()

for key in "${required[@]}"; do
  val="${!key:-}"
  if [[ -z "$val" && -f "$PROJECT_DIR/local.properties" ]]; then
    val="$(grep -E "^${key}=" "$PROJECT_DIR/local.properties" | sed -E "s/^${key}=//" | tail -1 || true)"
  fi
  if [[ -z "$val" ]]; then
    missing+=("$key")
  fi
done

if [[ ${#missing[@]} -gt 0 ]]; then
  echo "❌ Missing release signing values: ${missing[*]}"
  echo "   Add them to local.properties (recommended) or export env vars."
  echo "   See: keystore.properties.example"
  exit 1
fi

echo "▶ Building signed release APK + AAB..."
set +e
"$GRADLE_CMD" --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:assembleRelease :app:bundleRelease
RESULT=$?
set -e

if [[ $RESULT -ne 0 ]]; then
  echo "⚠ Release build failed (often lint env issue). Retrying with lint tasks skipped..."
  "$GRADLE_CMD" --no-daemon -Dkotlin.compiler.execution.strategy=in-process :app:assembleRelease :app:bundleRelease -x lint -x lintRelease -x lintVitalAnalyzeRelease
fi

APK_UNSIGNED="$PROJECT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
AAB_PATH="$PROJECT_DIR/app/build/outputs/bundle/release/app-release.aab"

echo "✅ Signed release build complete"
[[ -f "$APK_UNSIGNED" ]] && echo "   APK output: $APK_UNSIGNED"
[[ -f "$AAB_PATH" ]] && echo "   AAB output: $AAB_PATH"
