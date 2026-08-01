# [D1] Gradle 멀티모듈 골격 — 헥사고날 아키텍처, 의존성 규칙 강제

라벨: `D1`, `setup` / 마일스톤: D1

## 배경 / 해결하려는 문제

코드를 담을 빌드 구조가 없다. 이 시스템은 헥사고날 아키텍처(포트와 어댑터)를 채택한다:
비즈니스 규칙(domain)은 프레임워크를 모르고, 외부 세계(웹, MCP, 배치, DB)는 어댑터로
분리되어 교체 가능하다. 이 구조가 규약(코딩 컨벤션)에 머물면 반드시 무너지므로,
Gradle 의존성 선언과 ArchUnit 테스트로 빌드 시점에 강제한다.

## 요구사항

**모듈 구성과 의존성 규칙**

| 모듈 | 역할 | 컴파일 의존 허용 | 금지 |
|---|---|---|---|
| `domain` | 엔티티, 상태머신, 결재 규칙, 예산 불변식 | 없음 (JDK 21 표준 라이브러리만) | Spring, JPA, Jackson 등 외부 라이브러리 전부 |
| `application` | 유스케이스, 포트 인터페이스 정의 | `domain`, spring-context(트랜잭션 경계용) | spring-web, JPA 구현체 |
| `adapter-web` | REST API + SSE, Spring Boot 실행 모듈 | `application`, `domain` | `infra` 클래스 직접 참조 (runtimeOnly 조립만 허용) |
| `adapter-mcp` | MCP 서버 | `application`, `domain` | `infra` 직접 참조 |
| `adapter-batch` | 성과 수집 스케줄러 | `application`, `domain` | `infra` 직접 참조 |
| `infra` | JPA 리포지토리, LLM 클라이언트, 외부 API 클라이언트 (포트 구현체) | `application`(포트 구현), `domain` | adapter 계열 참조 |

- 단일 배포: `adapter-web`이 Spring Boot 부트 모듈을 겸하고, `infra`와 다른 어댑터를
  `runtimeOnly`로 조립한다 (컴파일 타임에는 서로 보이지 않고 런타임에만 묶임)
- 의존성 역전: `application`이 포트 인터페이스를 소유하고 `infra`가 구현한다.
  방향은 항상 infra → application이며 그 역은 컴파일 에러

**빌드 설정**
- Gradle 9.6.1 래퍼 생성, `gradlew` 커밋 — CI와 로컬이 동일 빌드 도구 사용
- 버전 카탈로그(`gradle/libs.versions.toml`): Java 21(Temurin), Spring Boot 3.5.3,
  Spring AI BOM 1.0.0, ojdbc11 23.8.0.25.04
- 의존성 잠금(dependency locking) 활성화, 잠금 파일 커밋
- 서버 포트 8280, 바인딩 주소 `0.0.0.0` 명시 (이 PC는 IPv6 와일드카드 바인딩 장애
  이력이 있어 기본값에 의존하지 않는다 — `docs/ENVIRONMENT.md` 참조)

**아키텍처 테스트 (ArchUnit)**
- `domain` 패키지는 `org.springframework..`, `jakarta.persistence..`, `com.fasterxml..`
  import 금지
- adapter 계열 패키지는 `..infra..` import 금지
- `application`은 `..adapter..` import 금지
- 이 규칙들을 ArchUnit 테스트로 작성해 CI에서 상시 검증 (사람이 기억하는 규칙은 규칙이 아니다)

## 예외 케이스

- [ ] Spring Boot 3.5.3 플러그인이 Gradle 9.6.1을 지원하지 않는 경우 → Gradle을 내리지 않고
      Boot 마이너 버전 조정, 판단 근거를 `docs/ENVIRONMENT.md`에 기록
- [ ] 버전 카탈로그와 잠금 파일 불일치 → 빌드 실패가 정상 동작 (조용한 버전 드리프트 차단)
- [ ] 전이 의존성(transitive dependency)으로 domain에 외부 라이브러리가 유입 →
      ArchUnit이 import 레벨에서 잡는지 확인, 안 잡히면 규칙 보강

## 테스트 케이스

**빌드 검증**
- [ ] `./gradlew build` 전 모듈 성공
- [ ] `./gradlew :domain:dependencies` 출력에 외부 라이브러리 0건 (JDK만)
- [ ] `domain` 소스에 `import org.springframework.stereotype.Service;` 추가 → 컴파일 실패
- [ ] `adapter-web` 소스에서 `infra`의 구현 클래스 import → 컴파일 실패
- [ ] 잠금 파일과 다른 버전을 강제 지정 → 빌드 실패

**ArchUnit**
- [ ] domain의 Spring/JPA/Jackson import 금지 규칙 3건 각각 통과
- [ ] 위반 코드를 일부러 넣어 규칙이 실제로 실패하는지 확인 (규칙이 검사할 클래스를 못 찾아도
      통과로 보이므로, 통과만으로는 규칙이 동작한다는 증거가 되지 않는다)
- [ ] application → adapter 방향 참조 금지 규칙 통과

**실행 검증**
- [ ] `./gradlew :adapter-web:bootRun` → 8280 포트 리슨 확인 (`netstat` 또는 헬스 호출)
- [ ] 바인딩 주소가 설정값(`0.0.0.0`)과 일치

## 완료 조건

- [ ] 모듈 6개, 의존성 표와 실제 build.gradle 선언이 일치
- [ ] ArchUnit 테스트가 CI에서 실행되는 상태
- [ ] 래퍼, 버전 카탈로그, 잠금 파일 커밋 완료
