#!/bin/sh
set -eu
APP_ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
if [ -d "$APP_ROOT/.tools/amazon-corretto-21.jdk/Contents/Home" ]; then
  JAVA_HOME="$APP_ROOT/.tools/amazon-corretto-21.jdk/Contents/Home"
  export JAVA_HOME
fi
MODE="${1:-local}"
case "$MODE" in
  local) BACKEND=emulator ;;
  live) BACKEND=live ;;
  *) echo "Usage: $0 [local|live]" >&2; exit 2 ;;
esac
cd "$APP_ROOT/android"
BUILD_ROOT="${REA_BUILD_ROOT:-${TMPDIR:-/tmp}/rea-android-build-$MODE}"
if [ "$MODE" = live ]; then
  ./gradlew "-PbuildRoot=$BUILD_ROOT" "-PnoticeBackend=$BACKEND" assembleDebug testDebugUnitTest lintDebug assembleRelease testReleaseUnitTest lintRelease
else
  ./gradlew "-PbuildRoot=$BUILD_ROOT" "-PnoticeBackend=$BACKEND" assembleDebug testDebugUnitTest lintDebug
fi
mkdir -p "$APP_ROOT/releases"
if [ "$MODE" = live ]; then
  cp "$BUILD_ROOT/app/outputs/apk/release/app-release.apk" "$APP_ROOT/releases/antihack-tour-live.apk"
else
  cp "$BUILD_ROOT/app/outputs/apk/debug/app-debug.apk" "$APP_ROOT/releases/antihack-tour-$MODE.apk"
fi
echo "Verified $MODE build: $APP_ROOT/releases/antihack-tour-$MODE.apk"
