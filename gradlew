#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROPS_FILE="$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.properties"

if [[ ! -f "$PROPS_FILE" ]]; then
  echo "Missing Gradle wrapper properties: $PROPS_FILE" >&2
  exit 1
fi

DIST_URL_RAW="$(awk -F= '/^distributionUrl=/{print $2}' "$PROPS_FILE")"
if [[ -z "$DIST_URL_RAW" ]]; then
  echo "distributionUrl is not set in $PROPS_FILE" >&2
  exit 1
fi

DIST_URL="${DIST_URL_RAW//\\:/:}"
DIST_ZIP_NAME="${DIST_URL##*/}"
DIST_BASENAME="${DIST_ZIP_NAME%.zip}"
GRADLE_DIR_NAME="${DIST_BASENAME%-bin}"
GRADLE_DIR_NAME="${GRADLE_DIR_NAME%-all}"
GRADLE_HOME_DIR="$SCRIPT_DIR/.gradle-dist/$GRADLE_DIR_NAME"
GRADLE_BIN="$GRADLE_HOME_DIR/bin/gradle"

if [[ ! -x "$GRADLE_BIN" ]]; then
  mkdir -p "$SCRIPT_DIR/.gradle-dist"
  TMP_ZIP="$SCRIPT_DIR/.gradle-dist/$DIST_ZIP_NAME"

  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$DIST_URL" -o "$TMP_ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$TMP_ZIP" "$DIST_URL"
  else
    echo "Neither curl nor wget is installed; cannot download $DIST_URL" >&2
    exit 1
  fi

  unzip -q -o "$TMP_ZIP" -d "$SCRIPT_DIR/.gradle-dist"
fi

exec "$GRADLE_BIN" "$@"
