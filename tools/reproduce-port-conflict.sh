#!/usr/bin/env bash
# Oracle 컨테이너 포트(1522)가 이미 점유된 상태의 기동 실패를 재현한다.
# 실행 후 컨테이너는 다시 기동된 상태로 남는다.
set -uo pipefail

PORT=1522

echo "== 사전 확인: 현재 컨테이너 상태"
docker ps --filter name=camp-oracle --format '{{.Names}}  {{.Status}}'

echo "== 컨테이너 정지 (stop 이므로 데이터는 남는다)"
docker compose stop oracle

echo "== ${PORT} 를 다른 프로세스가 점유하게 만든다"
python -c "import socket,time;s=socket.socket();s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1);s.bind(('127.0.0.1',${PORT}));s.listen();time.sleep(60)" &
BLOCKER=$!
sleep 2
netstat -ano | grep ":${PORT}" | grep LISTENING

echo "== 이 상태에서 기동 시도"
docker compose up -d oracle
echo "-- 종료 코드: $?"

echo "== 점유 해제"
# bash 의 kill 은 Windows 프로세스를 끝내지 못해 포트가 남는다. PID 로 taskkill 한다.
kill "$BLOCKER" 2>/dev/null
PID=$(netstat -ano | grep ":${PORT}" | grep LISTENING | awk '{print $5}' | sort -u | head -1)
[ -n "$PID" ] && taskkill //PID "$PID" //F
sleep 2
netstat -ano | grep ":${PORT}" | grep LISTENING || echo "${PORT} 비었음"

echo "== 다시 기동"
docker compose up -d oracle
docker ps --filter name=camp-oracle --format '{{.Names}}  {{.Status}}'
