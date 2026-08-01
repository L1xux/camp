#!/usr/bin/env bash
# Conventional Commits 를 벗어난 커밋을 경고로 알린다. 빌드를 실패시키지는 않는다.
set -uo pipefail

BASE="${BASE:?BASE 가 필요하다}"
HEAD="${HEAD:?HEAD 가 필요하다}"
PATTERN='^(feat|fix|refactor|test|docs|chore|perf|build|ci|revert)(\([^)]+\))?!?: .+'

merge_base=$(git merge-base "$BASE" "$HEAD")
bad=0

while IFS= read -r line; do
  [ -z "$line" ] && continue
  sha="${line%% *}"
  subject="${line#* }"
  if ! printf '%s' "$subject" | grep -Eq "$PATTERN"; then
    echo "::warning::Conventional Commits 형식이 아니다: $sha $subject"
    bad=$((bad + 1))
  fi
done < <(git log --no-merges --format='%h %s' "$merge_base..$HEAD")

if [ "$bad" -gt 0 ]; then
  echo "규약을 벗어난 커밋 ${bad}건. 형식: feat|fix|refactor|test|docs|chore: 설명"
else
  echo "커밋 메시지 규약 통과"
fi
