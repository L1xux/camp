#!/usr/bin/env bash
# gitleaks 실행 파일을 찾아 스테이징된 변경만 스캔한다.
# 공식 pre-commit 훅은 Go 툴체인을 요구해서 이 PC 에서 쓸 수 없다.
set -euo pipefail

WINGET_PATH="$HOME/AppData/Local/Microsoft/WinGet/Packages/Gitleaks.Gitleaks_Microsoft.Winget.Source_8wekyb3d8bbwe/gitleaks.exe"

if command -v gitleaks >/dev/null 2>&1; then
  GITLEAKS=$(command -v gitleaks)
elif [ -x "$WINGET_PATH" ]; then
  GITLEAKS="$WINGET_PATH"
else
  echo "gitleaks 를 찾을 수 없다. 설치 경로는 docs/ENVIRONMENT.md 참조." >&2
  exit 1
fi

exec "$GITLEAKS" git --staged --no-banner --redact "$@"
