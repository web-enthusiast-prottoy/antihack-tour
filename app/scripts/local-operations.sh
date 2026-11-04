#!/bin/sh
set -eu
cd "$(dirname "$0")"
exec ./admin.sh exec -- node ../firebase/scripts/operations.mjs
