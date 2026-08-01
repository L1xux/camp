#!/usr/bin/env bash
# 수기 코드 diff 가 상한을 넘으면 경고한다. 자동 생성 파일은 세지 않는다.
set -uo pipefail

BASE="${BASE:?BASE 가 필요하다}"
HEAD="${HEAD:?HEAD 가 필요하다}"
LIMIT="${LIMIT:-400}"

merge_base=$(git merge-base "$BASE" "$HEAD")
handwritten=0
generated=0

while read -r added deleted path; do
  # 바이너리는 줄 수가 '-' 로 나온다.
  [ "$added" = "-" ] && continue

  case "$path" in
    *gradle.lockfile | gradlew | gradlew.bat | gradle/wrapper/*)
      generated=$((generated + added + deleted))
      ;;
    *)
      handwritten=$((handwritten + added + deleted))
      ;;
  esac
done < <(git diff --numstat "$merge_base" "$HEAD")

echo "수기 코드 ${handwritten}줄, 자동 생성 파일 ${generated}줄 (상한 ${LIMIT}줄)"

if [ "$handwritten" -gt "$LIMIT" ]; then
  echo "::warning::수기 코드 diff 가 ${handwritten}줄로 상한 ${LIMIT}줄을 넘었다. 쪼갤 수 있는지 검토한다."
fi
