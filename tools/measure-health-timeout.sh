#!/usr/bin/env bash
# DB 를 정지시킨 상태에서 헬스 엔드포인트가 DOWN 을 반환하기까지 걸리는 시간을 잰다.
# 앱이 이미 떠 있어야 한다. 실행 후 DB 는 다시 기동시킨다.
set -u

HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8280/actuator/health}"

echo "== 사전 확인: 앱이 떠 있고 DB 가 붙어 있는가"
curl -s -o /dev/null -w "HTTP %{http_code} | time_total=%{time_total}s\n" "$HEALTH_URL"

echo "== DB 정지"
docker compose stop oracle

echo "== DB 정지 상태에서 헬스 호출"
curl -s -o /dev/null -w "HTTP %{http_code} | time_total=%{time_total}s\n" "$HEALTH_URL"

echo "== DB 재기동"
docker compose start oracle
until [ "$(docker inspect --format '{{.State.Health.Status}}' camp-oracle)" = "healthy" ]; do sleep 5; done
echo "oracle healthy"

echo "== 커넥션 풀 회복 확인"
curl -s -o /dev/null -w "HTTP %{http_code} | time_total=%{time_total}s\n" "$HEALTH_URL"
