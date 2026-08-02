#!/usr/bin/env python3
"""애플리케이션 로그가 전 건 유효한 JSON 이고 필수 필드를 갖췄는지 확인한다.

사용법: python tools/verify-json-logs.py <로그파일>

Gradle 진행 출력처럼 '{' 로 시작하지 않는 줄은 애플리케이션 로그가 아니므로 건너뛰고,
건너뛴 줄을 함께 보고해 검사 대상이 조용히 비는 것을 막는다.
"""

import json
import sys

REQUIRED_FIELDS = ("timestamp", "level", "event", "request_id")


def main(path: str) -> int:
    checked = 0
    skipped = []

    with open(path, encoding="utf-8") as f:
        for line_no, line in enumerate(f, 1):
            if not line.startswith("{"):
                skipped.append((line_no, line.rstrip()))
                continue
            try:
                record = json.loads(line)
            except json.JSONDecodeError as e:
                print(f"FAIL {line_no}행: JSON 파싱 실패 ({e})")
                return 1
            missing = [field for field in REQUIRED_FIELDS if field not in record]
            if missing:
                print(f"FAIL {line_no}행: 필수 필드 없음 {missing}")
                return 1
            checked += 1

    if checked == 0:
        print("FAIL: 검사한 로그가 0건이다")
        return 1

    print(f"OK: {checked}건 전부 유효한 JSON, 필수 필드 {list(REQUIRED_FIELDS)} 모두 존재")
    print(f"건너뛴 줄 {len(skipped)}건 (애플리케이션 로그가 아님)")
    for line_no, text in skipped:
        print(f"  {line_no}: {text}")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1]))
