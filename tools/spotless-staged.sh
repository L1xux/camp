#!/usr/bin/env bash
# 포맷을 맞춘다. 파일이 바뀌면 pre-commit 이 그것을 감지해 커밋을 멈춘다.
set -euo pipefail

DEFAULT_JDK="/c/Program Files/Eclipse Adoptium/jdk-21.0.9.10-hotspot"
if [ -z "${JAVA_HOME:-}" ] && [ -d "$DEFAULT_JDK" ]; then
  export JAVA_HOME="$DEFAULT_JDK"
fi

exec ./gradlew --quiet spotlessApply
