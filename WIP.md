# 작업 인수인계 — 이슈 #32

이 파일은 세션이 끊긴 자리를 이어받기 위한 임시 문서다. **PR 머지 전에 삭제한다.**

- 브랜치: `feat/32-local-env` (커밋, 푸시 완료)
- 이슈: https://github.com/L1xux/camp/issues/32
- 상태: 구현과 문서는 끝. **미검증 8건을 검증한 뒤 PR 을 올려야 한다.**
- PR 은 아직 만들지 않았다.

## 먼저 읽을 것

1. `CLAUDE.md` — 특히 "사실을 쓸 때", "이슈 착수부터 머지까지", "GitHub 에 올리기 전 승인"
2. 이슈 #32 본문 (체크리스트 31건의 원본)

## 지금까지 한 일

Oracle 컨테이너와 Liquibase 마이그레이션 체계를 만들고, 이슈의 테스트 케이스를 실행했다.
체크리스트 31건 중 30건 완료, `gitleaks` 1건은 #33 이후 UiJin 이 확인하기로 유예.

만든 파일: `docker-compose.yml`, `.env.example`, `docs/RUNBOOK.md`, `README.md`,
`docs/adr/003-마이그레이션-도구-Liquibase.md`,
`infra/src/main/resources/db/changelog/` (master + V001)

고친 파일: `CLAUDE.md`, `docs/ENVIRONMENT.md`, `adapter-web/build.gradle`,
`adapter-web/src/main/resources/application.yml`, `infra/build.gradle`,
`gradle/libs.versions.toml`

작업 중 발견해 고친 것:
- `bootRun` 작업 디렉터리가 `adapter-web/` 이라 루트 `.env` 를 못 읽어 `ORA-01017` 발생.
  `workingDir = rootProject.projectDir` 로 고정.
- DB 정지 시 헬스 엔드포인트가 15초 멈춤. Hikari `connection-timeout: 5000` 설정으로 5.5초에 `DOWN`.

## 남은 일: 미검증 8건

문서에 확정형으로 써놓고 실제로 확인하지 않은 것들이다. 하나씩 검증하고,
결과가 문서와 다르면 문서를 고친다. 검증 후 `<!-- verified: ... -->` 표시를 단다.

| # | 대상 | 무엇을 확인해야 하나 |
|---|---|---|
| 1 | `docs/adr/003-*.md` 의 "Liquibase rollback 을 도구가 실행할 수 있다" | `liquibase rollback` 을 실제로 실행해 V001 이 되돌아가는지. **Liquibase 를 고른 근거 자체라 가장 중요하다** |
| 2 | `docs/RUNBOOK.md` 잠금 해제 SQL | `DATABASECHANGELOGLOCK` 의 컬럼명(`locked`, `lockgranted`, `lockedby`, `id`)이 맞는지, UPDATE 가 실제로 동작하는지 |
| 3 | `docs/RUNBOOK.md` "실패한 changeset 을 고치면 이어서 적용된다" | 깨진 V003 을 만들어 실패시킨 뒤 고쳐서 재기동 → 그 지점부터 이어지는지 |
| 4 | `application.yml` 의 `oracle.net.CONNECT_TIMEOUT`, `oracle.jdbc.ReadTimeout` | Hikari `connection-timeout` 과 동시에 넣어 효과 분리가 안 됨. Hikari 설정을 빼고 Oracle 속성만으로 재보기 |
| 5 | `docker-compose.yml` 의 `127.0.0.1:1522` 바인딩 | `netstat` 으로 1522 가 루프백에만 바인딩됐는지 |
| 6 | `V001__baseline.sql` 의 `uk_brand_code` | 같은 `code` 중복 삽입이 실제로 거부되는지 |
| 7 | `docker-compose.yml` 의 `${ORACLE_PASSWORD:?...}` | `.env` 없이 `docker compose up` 할 때 그 메시지가 나오는지 |
| 8 | `docs/RUNBOOK.md` 시크릿 교체 절차 | `.env` 비밀번호를 바꾸고 `down`/`up` 하면 실제로 반영되는지 |

## 검증 후 할 일

1. 문서에 `<!-- verified: 날짜 | 명령 -->` 표시 추가, 틀린 서술은 수정
2. 커밋, 푸시
3. **PR 본문 초안을 UiJin 에게 보여주고 승인받은 뒤** PR 생성
4. 이슈 #32 의 체크박스 체크
5. **이 파일(`WIP.md`) 삭제**

## 환경 메모

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.9.10-hotspot"
docker compose up -d          # healthy 까지 40초 안팎
./gradlew :adapter-web:bootRun
```

- 앱 계정: `.env` 의 `DB_USER=camp`, `DB_PASSWORD=camp_app_local_2026`
- 컨테이너 접속: `docker exec -i camp-oracle sqlplus -s camp/비밀번호@//localhost:1521/FREEPDB1`
- 헬스 확인: `curl http://127.0.0.1:8280/actuator/health`
- 앱 종료: `netstat -ano | grep ":8280" | grep LISTENING` 으로 PID 찾아 `taskkill //PID N //F`
- 멀티라인 `python -c` 는 이 셸에서 깨진다. 파일로 빼서 실행할 것
