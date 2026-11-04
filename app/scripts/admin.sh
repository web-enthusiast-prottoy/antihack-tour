#!/bin/sh
set -eu
APP_ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
if [ -x "$APP_ROOT/.tools/node/bin/node" ]; then
  PATH="$APP_ROOT/.tools/node/bin:$PATH"
  export PATH
fi
if [ -d "$APP_ROOT/.tools/amazon-corretto-21.jdk/Contents/Home" ]; then
  JAVA_HOME="$APP_ROOT/.tools/amazon-corretto-21.jdk/Contents/Home"
  export JAVA_HOME
fi
FIREBASE_EMULATORS_PATH="$APP_ROOT/.tools/firebase-emulators"
export FIREBASE_EMULATORS_PATH
cd "$APP_ROOT/admin"
exec npm "$@"
