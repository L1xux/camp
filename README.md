# CAMP (Campaign & Marketing Platform)

패션 브랜드의 인플루언서, PPL 마케팅 운영 시스템과 대화형 동적 대시보드.

인플루언서 섭외부터 계약, 결재, 집행, 성과 확인까지 엑셀과 메신저로 흩어져 있는 과정을
한 시스템으로 모으고, 쌓인 성과 데이터를 자연어로 질의할 수 있게 만든다.

자세한 기획은 [docs/PLAN.md](docs/PLAN.md) 참조.

## 현재 상태

D1 진행 중. 빌드 골격과 로컬 실행 환경까지 완료했고 기능 구현은 아직 없다.
진행 상황은 [GitHub Issues](https://github.com/L1xux/camp/issues) 참조.

## 기술 스택

| 구분 | 선택 | 근거 |
|---|---|---|
| 언어, 프레임워크 | Java 21, Spring Boot 3.5.16 | [ADR-001](docs/adr/001-기술스택-선정.md) |
| 빌드 | Gradle 8.14.5 | [ADR-002](docs/adr/002-빌드-툴체인-버전조합.md) |
| DB | Oracle Database Free 23ai | [ADR-001](docs/adr/001-기술스택-선정.md) |
| 마이그레이션 | Liquibase 4.31.1 | [ADR-003](docs/adr/003-마이그레이션-도구-Liquibase.md) |
| AI | Spring AI 1.1.8 | [ADR-001](docs/adr/001-기술스택-선정.md) |
| 프론트 | React, TypeScript, Vite | [ADR-001](docs/adr/001-기술스택-선정.md) |

## 모듈 구조

```
domain      <- application <- adapter-web / adapter-mcp / adapter-batch
                           <- infra
```

| 모듈 | 하는 일 |
|---|---|
| `domain` | 결재 규칙, 예산 불변식, 캠페인 상태 전이. 라이브러리 의존성이 없다 |
| `application` | 유스케이스와 포트 인터페이스 |
| `infra` | 포트 구현체 (DB, 외부 API) |
| `adapter-web` | REST API, 앱 실행 모듈 |
| `adapter-mcp` | MCP 서버 |
| `adapter-batch` | 배치 스케줄러 |

의존성 규칙은 `ArchitectureTest` 가 빌드마다 검증한다.

## 실행 방법

**필요한 것**: Docker, JDK 21 (Temurin). Gradle 은 래퍼가 있어 설치하지 않아도 된다.

```bash
# 1. 환경 변수
cp .env.example .env        # 값을 채운다

# 2. DB 기동 (healthy 까지 40초 안팎)
docker compose up -d
docker inspect --format '{{.State.Health.Status}}' camp-oracle

# 3. 앱 기동
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.9.10-hotspot"
./gradlew :adapter-web:bootRun

# 4. 확인
curl http://127.0.0.1:8280/actuator/health
```

컨테이너가 `healthy` 가 되기 전에 앱을 띄우면 `ORA-12514` 로 실패한다.

포트는 백엔드 8280, Oracle 1522 를 쓴다. 다른 프로젝트와 충돌을 피하기 위한 것이다.

### 데이터 보존 정책

로컬은 데이터 소실을 허용한다. 볼륨을 두지 않았으므로 `docker compose down` 하면 DB 가 비워지고,
다시 띄우면 Liquibase 가 마이그레이션을 처음부터 적용한다.
시드 데이터가 필요하면 시드 스크립트를 다시 실행한다 (이슈 #42 에서 추가).
`docker compose stop` 과 `start` 는 데이터를 유지한다.
<!-- verified: 2026-08-02 | docker compose down && up -d 후 bootRun 로그에 V001 두 changeset 재적용 -->

## 빌드와 테스트

```bash
./gradlew build                              # 컴파일, 테스트, 아키텍처 검증
./gradlew resolveAndLockAll --write-locks    # 의존성 변경 후 잠금 갱신
```

## 문서

| 문서 | 내용 |
|---|---|
| [docs/PLAN.md](docs/PLAN.md) | 기획: 문제 정의, 범위, 아키텍처, 품질 목표 |
| [docs/adr/](docs/adr/) | 되돌리기 비싼 결정의 근거 |
| [docs/RUNBOOK.md](docs/RUNBOOK.md) | 운영 절차: 기동, DB 초기화, 마이그레이션 복구 |
| [docs/ENVIRONMENT.md](docs/ENVIRONMENT.md) | 이 PC 의 환경 문제와 조치 이력 |
| [CLAUDE.md](CLAUDE.md) | 레포 규약: 글쓰기, 검증 기록, 작업 절차 |
