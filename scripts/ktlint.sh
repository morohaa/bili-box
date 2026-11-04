#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="1.8.0"
JAR="$ROOT_DIR/build/tools/ktlint-cli-$VERSION-all.jar"
URL="https://repo1.maven.org/maven2/com/pinterest/ktlint/ktlint-cli/$VERSION/ktlint-cli-$VERSION-all.jar"
SHA256="369ad2b789f95a011f807e1fcb690ccef80bd7cd014fd139e73ae82dcc0baeab"
MODE="${1:-check}"

if [[ ! -f "$JAR" ]]; then
  mkdir -p "$(dirname "$JAR")"
  echo "Downloading ktlint $VERSION..."
  curl -fsSL --retry 3 -o "$JAR.tmp" "$URL"
  if command -v sha256sum >/dev/null 2>&1; then
    echo "$SHA256  $JAR.tmp" | sha256sum -c -
  else
    echo "$SHA256  $JAR.tmp" | shasum -a 256 -c -
  fi
  mv "$JAR.tmp" "$JAR"
fi

JAVA_BIN="java"
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
fi

FILES=()
while IFS= read -r file; do
  FILES+=("$file")
done < <(find "$ROOT_DIR/app/src" -type f -name '*.kt' | sort)

case "$MODE" in
  check)
    exec "$JAVA_BIN" -jar "$JAR" --editorconfig="$ROOT_DIR/.editorconfig" "${FILES[@]}"
    ;;
  format)
    exec "$JAVA_BIN" -jar "$JAR" --editorconfig="$ROOT_DIR/.editorconfig" --format "${FILES[@]}"
    ;;
  *)
    echo "Usage: $0 [check|format]" >&2
    exit 2
    ;;
esac
