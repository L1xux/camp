# 개발 환경 — 알려진 문제와 검증 절차

## 1. Winsock 카탈로그 손상 (2026-08-01 발견 → **2026-08-02 해결됨**)

> **해결 확인 (2026-08-02)**: `netsh winsock reset` + 재부팅 후 재검증.
> `java tools/SockDiag.java` 8줄 전부 OK — 문제였던 `bind ::` 포함. `git ls-remote origin` 정상.
> 아래 내용은 재발 시 참조용 기록으로 남긴다. Docker 빌드 우회는 불필요해졌다.


### 증상

- `git push` / `curl` → `getaddrinfo() thread failed to start`, `getsockname() failed with errno 10022`
- **Gradle이 시작조차 못 함** → `java.net.SocketException: Bad address: listen`
  (데몬·FileLockContentionHandler가 와일드카드 주소에 바인딩하다 실패)
- 외부 HTTP는 간헐적으로 성공/실패 (같은 URL이 200 → 연결 실패로 오락가락)

### 진단 결과 (`tools/SockDiag.java`)

| 시도 | 결과 |
|---|---|
| `preferIPv4Stack=true` 로 전체 바인딩 | **전부 실패** — 순수 IPv4 소켓 스택이 죽어 있음 |
| 듀얼스택 + `127.0.0.1` | OK |
| 듀얼스택 + `0.0.0.0` | OK |
| 듀얼스택 + `::1` | OK |
| 듀얼스택 + `::` (IPv6 와일드카드) | **실패** (`Bad address: listen`) |
| DNS 조회 | 간헐적 실패 |

`netsh winsock show catalog` 에서 **MSAFD 공급자 항목이 두 벌 중복 등록**(총 28개)된 것을 확인.
`Tcpip6\Parameters\DisabledComponents`는 미설정(정상), IPv6 자체는 활성.
→ 원인은 IPv6 설정이 아니라 **Winsock 카탈로그 손상**.

JVM 옵션으로는 우회 불가 — 와일드카드 주소 선택을 바꾸는 표준 스위치가 없고,
`preferIPv4Stack=true`는 IPv4 스택이 죽어 있어 오히려 전부 실패한다.

### 조치 (관리자 PowerShell → 재부팅 필수)

```powershell
netsh winsock reset
netsh int ip reset
netsh int ipv6 reset
ipconfig /flushdns
# 이후 재부팅
```

### 재부팅 후 검증 체크리스트

```bash
# 1. 소켓 스택 — 6줄 모두 OK 여야 함 (특히 'bind ::')
java tools/SockDiag.java

# 2. git 네트워크
git ls-remote origin

# 3. Gradle 기동
./gradlew --version
```

1번의 `bind ::` 가 여전히 FAIL 이면 Gradle은 못 돈다. 그 경우의 대안(미검증):
Docker 컨테이너 안에서 빌드(호스트 Winsock 우회) → `docker run --rm -v $PWD:/w -w /w gradle:9.6.1-jdk21 gradle build`

## 1-2. 이슈 #32 에서 겪은 문제 (2026-08-02)

**`bootRun` 이 레포 루트의 `.env` 를 못 읽음**

`bootRun` 의 기본 작업 디렉터리는 실행 모듈 디렉터리(`adapter-web/`)다.
`spring.config.import: optional:file:./.env[.properties]` 가 `adapter-web/.env` 를 찾아
아무것도 못 읽었고, DB 접속이 `ORA-01017: invalid credential` 로 실패했다.

조치: `adapter-web/build.gradle` 에서 `bootRun.workingDir = rootProject.projectDir` 로 고정.

**DB 가 멈추면 헬스 엔드포인트가 응답하지 않음**

컨테이너를 정지시킨 상태에서 `/actuator/health` 를 호출하니 `DOWN` 을 반환하지 않고
15초 타임아웃까지 멈춰 있었다. 커넥션 획득 타임아웃이 없어서다.

조치: `spring.datasource.hikari.connection-timeout: 5000` 설정. 이후 5초 만에 `DOWN` 을 반환한다.
DB 를 다시 띄우면 앱 재기동 없이 커넥션 풀이 회복된다.
재현은 `tools/measure-health-timeout.sh` 로 한다.

```
== 사전 확인: 앱이 떠 있고 DB 가 붙어 있는가
HTTP 200 | time_total=0.160533s
== DB 정지 상태에서 헬스 호출
HTTP 503 | time_total=5.041087s
== 커넥션 풀 회복 확인
HTTP 200 | time_total=0.006613s
```
<!-- verified: 2026-08-02 | bash tools/measure-health-timeout.sh -->

처음에는 Hikari 설정과 함께 `oracle.net.CONNECT_TIMEOUT`, `oracle.jdbc.ReadTimeout` 도 넣었으나
효과를 분리해 재보니 Oracle 속성은 기여하지 않아 제거했다. 설정을 하나씩만 남기고 잰 결과다.

| 남긴 설정 | DB 정지 상태의 헬스 응답 |
|---|---|
| Hikari `connection-timeout: 5000`, `validation-timeout: 3000` | `HTTP 503 \| time_total=5.017912s` |
| Oracle `net.CONNECT_TIMEOUT: 5000`, `jdbc.ReadTimeout: 10000` | `HTTP 503 \| time_total=30.040854s` |

30초는 Hikari `connection-timeout` 의 기본값이다. 컨테이너를 정지하면 연결이 늘어지는 것이 아니라
포트가 닫히므로, 연결 수립 대기 시간을 제한하는 Oracle 속성은 이 시나리오에서 발동하지 않는다.
포트는 열린 채 응답만 느려지는 장애에서의 효과는 측정하지 않았다.
<!-- verified: 2026-08-02 | 설정별로 application.yml 을 바꿔 bootRun 후 docker compose stop oracle, curl -w time_total -->
<!-- unverified: 포트가 열린 채 응답만 느려지는 장애는 재현하지 않음 -->

**컨테이너 기동 실패**

`docker compose up -d` 는 한 번에 성공했다. 헬스체크가 `healthy` 가 되기까지 40초 안팎이
걸린다. 그 전에 접속하면 `ORA-12514: FREEPDB1 서비스가 리스너에 등록되지 않았습니다` 가 난다.

*메모리 부족*: 겪지 않았다. Docker 에 7.58GiB 가 있어 Oracle Free 요건(2GB+)을 넘는다.
부족하면 Docker Desktop 의 Resources 에서 메모리를 올린다.
<!-- unverified: 메모리를 낮춰 재현하지 않음 -->

*포트 충돌*: 다른 프로세스가 1522 를 잡고 있으면 컨테이너가 뜨지 않는다. 재현은
`tools/reproduce-port-conflict.sh` 로 한다.

```
== 이 상태에서 기동 시도
 Container camp-oracle Starting
Error response from daemon: ports are not available: exposing port TCP 127.0.0.1:1522 -> 127.0.0.1:0:
listen tcp4 127.0.0.1:1522: bind: Only one usage of each socket address (protocol/network address/port)
is normally permitted.
-- 종료 코드: 1
```

조치는 점유 프로세스를 찾아 끝내는 것이다. `docker compose` 가 알아서 비켜주지 않는다.

```bash
netstat -ano | grep ":1522" | grep LISTENING          # 마지막 칸이 PID
taskkill //PID <PID> //F
docker compose up -d oracle
```

bash 의 `kill` 은 Windows 프로세스를 끝내지 못해 포트가 계속 잡혀 있다. `taskkill` 을 쓴다.
<!-- verified: 2026-08-02 | bash tools/reproduce-port-conflict.sh -->

## 2. 로컬 실행 포트

5173, 8000, 5273, 8273은 다른 프로젝트가 점유 중이므로 CAMP는 다음을 쓴다:

| 용도 | 포트 |
|---|---|
| 백엔드(Spring Boot) | 8280 |
| 프론트(Vite) | 5280 |
| Oracle 23ai (컨테이너) | 1522 → 1521 |

**서버 바인딩은 반드시 `0.0.0.0` 또는 `127.0.0.1`을 명시**한다. 와일드카드 기본값(`::`)에
의존하면 위 문제가 재발한다. (Winsock 복구 후에도 명시적 바인딩을 유지 — 재현성 확보)

Oracle 컨테이너는 `docker-compose.yml` 에서 `127.0.0.1:1522:1521` 로 매핑한다.
루프백에만 열려 외부에서 접근할 수 없다.

```
$ netstat -ano | grep ":1522" | grep LISTENING
  TCP    127.0.0.1:1522         0.0.0.0:0              LISTENING       18136
```
<!-- verified: 2026-08-02 | netstat -ano | grep ":1522" | grep LISTENING -->

## 3. 확정 버전 (2026-08-02 이슈 #31에서 실제 빌드로 검증)

| 대상 | 버전 | 비고 |
|---|---|---|
| JDK | Temurin 21.0.9 | 설치 확인됨 |
| Gradle | 8.14.5 | 래퍼 커밋됨. sha256 `6f74b601…e854` 검증 |
| Spring Boot | 3.5.16 | |
| Spring AI BOM | 1.1.8 | Boot 3.5 호환 라인 |
| Oracle JDBC (ojdbc11) | 23.26.3.0.0 | 23ai 계열 |
| ArchUnit | 1.4.2 | |
| Docker | 28.3.2 / 메모리 7.58GiB | Oracle Free 2GB+ 요건 충족 |

**Gradle을 9.6.1에서 8.14.5로 내렸다.** Spring Boot 3.5.x의 Gradle 플러그인이
Gradle 7.6.4+ / 8.4+ 만 지원하고 9는 지원하지 않기 때문이다. 판단 근거와 버린 대안
(Boot 4.1.0 + Spring AI 2.0.0)은 `docs/adr/002-빌드-툴체인-버전조합.md` 참조.

버전 단일 출처는 `gradle/libs.versions.toml`이며, 이 표는 그 사본이 아니라
"이 PC에서 실제로 빌드가 통과한 조합"의 기록이다.

## 4. 빌드 실행

래퍼가 레포에 있으므로 Gradle 별도 설치가 필요 없다.

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.9.10-hotspot"
./gradlew build
./gradlew :adapter-web:bootRun          # http://127.0.0.1:8280/actuator/health
./gradlew resolveAndLockAll --write-locks   # 의존성 변경 후 잠금 갱신
```

## 5. 도구 경로와 셸 제약 (2026-08-02)

winget 으로 설치한 도구는 이 셸의 `PATH` 에 잡히지 않는다. 매번 경로를 지정한다.

```bash
export PATH="$PATH:/c/Program Files/GitHub CLI"     # gh 2.97.0
/c/Users/oo/AppData/Local/Microsoft/WinGet/Packages/Gitleaks.Gitleaks_Microsoft.Winget.Source_8wekyb3d8bbwe/gitleaks.exe git --no-banner
```
<!-- verified: 2026-08-02 | gh auth status -> Logged in to github.com account L1xux / gitleaks git -> 32 commits scanned, no leaks found -->

`pre-commit` 은 pyenv 의 Python 3.11.9 에 설치했다. `pip install` 직후에는 shim 이 없어
`command not found` 가 난다. `pyenv rehash` 를 한 번 실행해야 `PATH` 에서 잡힌다.

```
$ pip install pre-commit && pre-commit --version
bash: pre-commit: command not found
$ pyenv rehash && pre-commit --version
pre-commit 4.6.1
```
<!-- verified: 2026-08-02 | 위 명령 그대로 -->

`gh` 는 `L1xux` 계정으로 인증돼 있고 토큰은 keyring 에 있다. 스코프는 `gist`, `read:org`,
`repo`, `workflow`. `gh auth login` 은 브라우저를 여는 대화형 절차라 Claude 가 실행할 수 없다.

**gitleaks 로 차단을 시연할 때 순차 문자열을 쓰면 탐지되지 않는다.** 기본 룰셋에 엔트로피
검사가 있어서 `sk-abcdefghijk...` 같은 값은 통과한다. 무작위 문자열을 써야 한다.

```
# 순차 문자열: sk- 뒤에 abcdefghij...0123456789 를 이어붙인 값
$ gitleaks dir . --no-banner
INF no leaks found

# 무작위 문자열: tr -dc 'A-Za-z0-9' < /dev/urandom 으로 만든 값
$ gitleaks dir . --no-banner
WRN leaks found: 1
```
<!-- verified: 2026-08-02 | 위 두 값을 각각 파일에 넣고 gitleaks dir 실행 -->

이 문서에 실제 형태의 키를 적으면 gitleaks 가 이 문서를 잡아 커밋이 막힌다. 실제로 한 번
막혔고, 그래서 값을 설명으로 대체했다.
<!-- verified: 2026-08-02 | 값을 그대로 적고 git commit -> gitleaks 훅이 leaks found: 1 로 차단 -->

**Bash 도구에서 PowerShell 문법을 쓰지 않는다.** here-string(`@'...'@`)을 커밋 메시지에
쓰면 리터럴 `@` 가 제목에 박힌다. 여러 줄 문자열은 heredoc 이나 `git commit -F -` 로 넘긴다.

```bash
git commit -F - <<'EOF'
제목

본문
EOF
```

멀티라인 `python -c` 도 이 셸에서 깨진다. 스크립트 파일로 빼서 실행한다.

## 6. 로컬 ↔ 원격 git 불일치 (**2026-08-02 해결됨**)

`git push`가 위 네트워크 문제로 실패해, 원격(GitHub)에는 MCP API로 파일을 올렸다.
그 결과 로컬과 원격이 **공통 조상이 없는 별개 계보**가 되어 있었다 (`git merge-base` 빈 출력).

정리 방식 — 로컬 커밋 5개를 `origin/main` 위로 rebase해 한 줄로 이어붙였다:

```bash
git branch backup-pre-rebase HEAD       # 안전망
git rebase origin/main                  # --allow-unrelated-histories 는 merge 전용, rebase엔 불필요
# add/add 충돌 2건(PLAN.md, ADR-001) → 로컬(--theirs) 채택
git push -u origin main
```

- 원격의 `Initial commit`과 `README.md`(빈 파일) 보존
- 원격에만 있던 중복 커밋 3개는 rebase 과정에서 로컬 커밋에 흡수됨
- 원격 ADR에 있던 오타(`두텅다`)는 로컬 버전 채택으로 해소
- 검증: `git diff backup-pre-rebase HEAD` → `README.md` 추가 외 차이 없음

**교훈**: 네트워크 장애 시 원격에 API로 직접 올리면 히스토리가 갈라진다.
다음에는 로컬 커밋만 쌓아두고 복구 후 push 한 번으로 처리한다.
