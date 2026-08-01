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

## 2. 로컬 실행 포트

5173·8000·5273·8273은 다른 프로젝트가 점유 중이므로 CAMP는 다음을 쓴다:

| 용도 | 포트 |
|---|---|
| 백엔드(Spring Boot) | 8280 |
| 프론트(Vite) | 5280 |
| Oracle 23ai (컨테이너) | 1522 → 1521 |

**서버 바인딩은 반드시 `0.0.0.0` 또는 `127.0.0.1`을 명시**한다. 와일드카드 기본값(`::`)에
의존하면 위 문제가 재발한다. (Winsock 복구 후에도 명시적 바인딩을 유지 — 재현성 확보)

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

## 5. 로컬 ↔ 원격 git 불일치 (**2026-08-02 해결됨**)

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
