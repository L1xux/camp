# 개발 환경 — 알려진 문제와 검증 절차

## 1. Winsock 카탈로그 손상 (2026-08-01 발견, D1 착수 시)

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

## 3. 검증된 버전 (2026-08-01 Maven Central / services.gradle.org 조회)

| 대상 | 버전 | 비고 |
|---|---|---|
| Gradle | 9.6.1 | sha256 `9c0f7fae…9e14` |
| JDK | Temurin 21.0.7 | 설치 확인됨 |
| Spring Boot | 3.5.3 | Gradle 9 호환 여부 D1에서 빌드로 확인 |
| Spring AI BOM | 1.0.0 | 정식 릴리스(M/RC 아님) |
| Oracle JDBC (ojdbc11) | 23.8.0.25.04 | 23ai 계열 |
| Docker | 28.3.2 / 메모리 7.58GiB | Oracle Free 2GB+ 요건 충족 |

## 4. 오프라인 자산

Gradle 배포판을 이미 받아뒀다 (재다운로드 불필요, 네트워크 불안정 대비):

```
<scratchpad>/gradle-9.6.1-bin.zip      (140,682,664 bytes, 체크섬 검증 완료)
<scratchpad>/gradle-9.6.1/bin/gradle   (압축 해제됨)
```

`<scratchpad>` =
`C:\Users\oo\AppData\Local\Temp\claude\C--Users-oo-file-project-tripbook\c619b220-d998-4b3b-a63d-78028e2638c4\scratchpad`

Winsock 복구 후 이 gradle로 래퍼를 생성한다:

```bash
cd C:/Users/oo/file/project/camp
<scratchpad>/gradle-9.6.1/bin/gradle wrapper --gradle-version 9.6.1
```

## 5. 로컬 ↔ 원격 git 불일치 (미해결)

`git push`가 위 네트워크 문제로 실패해, 원격(GitHub)에는 MCP API로 파일을 올렸다.
따라서 **로컬 커밋 히스토리와 원격 커밋 히스토리가 다르다.**

- 원격에 있는 것: `docs/PLAN.md`, `docs/adr/001-기술스택-선정.md` (내용은 로컬과 동일)
- 로컬에만 있는 것: 커밋 4개 + 이 문서 + `tools/SockDiag.java`

네트워크 복구 후 정리 순서:

```bash
git fetch origin
git rebase origin/main     # 또는 내용이 같으므로 로컬 기준으로 강제 정렬 판단
git push -u origin main
```
