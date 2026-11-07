#!/bin/sh
set -eu
APP_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
if [ -d "$APP_ROOT/.tools/amazon-corretto-21.jdk/Contents/Home" ]; then
  JAVA_HOME="$APP_ROOT/.tools/amazon-corretto-21.jdk/Contents/Home"
  export JAVA_HOME
fi
MODE="${1:-local}"
if [ "$#" -gt 0 ]; then shift; fi
BUILD_ROOT="${REA_BUILD_ROOT:-${TMPDIR:-/tmp}/rea-android-build-$MODE}"
OUTPUT="$APP_ROOT/releases"
case "$MODE" in
  local|live|admin-live) ;;
  *) echo "Usage: $0 [local|live|admin-live]" >&2; exit 2 ;;
esac
cd "$APP_ROOT/android"
mkdir -p "$OUTPUT"
case "$MODE" in
  local)
    ./gradlew "-PbuildRoot=$BUILD_ROOT" -PnoticeBackend=emulator \
      assemblePlayerDebug testPlayerDebugUnitTest lintPlayerDebug \
      assembleAdminDebug testAdminDebugUnitTest lintAdminDebug
    cp "$BUILD_ROOT/app/outputs/apk/player/debug/app-player-debug.apk" "$OUTPUT/antihack-player-local.apk"
    cp "$BUILD_ROOT/app/outputs/apk/admin/debug/app-admin-debug.apk" "$OUTPUT/antihack-admin-local.apk"
    ;;
  live)
    ./gradlew "-PbuildRoot=$BUILD_ROOT" -PnoticeBackend=live \
      assemblePlayerDebug testPlayerDebugUnitTest lintPlayerDebug \
      assemblePlayerRelease testPlayerReleaseUnitTest lintPlayerRelease
    cp "$BUILD_ROOT/app/outputs/apk/player/release/app-player-release.apk" "$OUTPUT/antihack-tour-live-candidate.apk"
    ;;
  admin-live)
    ./gradlew "-PbuildRoot=$BUILD_ROOT" -PnoticeBackend=live -PadminLive=true \
      assembleAdminRelease testAdminReleaseUnitTest lintAdminRelease "$@"
    cp "$BUILD_ROOT/app/outputs/apk/admin/release/app-admin-release.apk" "$OUTPUT/antihack-admin-live-candidate.apk"
    ;;
esac
echo "Build, unit tests and lint passed. APK output: $OUTPUT"
