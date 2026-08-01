# ADR-003: DB 마이그레이션 도구로 Liquibase 선택

- 상태: **채택** (2026-08-02)
- 상황: 스키마 변경을 버전 관리하는 도구가 필요하다. 후보는 Flyway 와 Liquibase.
  개발 사이클 규칙상 "마이그레이션에는 롤백 경로를 함께 작성한다" 가 요구사항이다.

## 확인한 사실 (2026-08-02)

| 항목 | 확인 내용 |
|---|---|
| Flyway Undo | Community Edition 에 없다. Pro 또는 Enterprise 가 필요하다 |
| Liquibase rollback | 오픈소스에서 지원한다 (`--rollback` 주석 또는 rollback 블록). V001 로 실행 확인 |
| Oracle 의 DDL 처리 | DDL 앞뒤로 암묵적 커밋이 일어난다. 도구와 무관하게 DDL 의 트랜잭션 롤백은 불가능하다 |
| Spring Boot 3.5.16 관리 버전 | Flyway 11.7.2, Liquibase 4.31.1 |

## 결정

**Liquibase 4.31.1**, SQL 형식 changelog 를 쓴다.

## 이유

1. 롤백을 도구가 실행할 수 있다. Flyway Community 로는 undo 파일을 만들어도 실행할 수단이 없어
   규칙이 문서로만 남는다. Liquibase CLI 로 V001 을 되돌려 `brand` 테이블이 사라지고 이력이
   0건이 되는 것, `update` 로 다시 적용되는 것을 확인했다. 명령은 `docs/RUNBOOK.md` 에 있다.
   <!-- verified: 2026-08-02 | liquibase/liquibase:4.31.1 컨테이너로 rollbackCount 2 실행 후 user_tables, databasechangelog 조회 -->
2. SQL 형식 changelog 를 쓰면 Flyway 의 장점인 "순수 SQL 가독성" 을 대부분 유지한다.
   XML 이나 YAML 로 스키마를 기술하지 않는다.
3. Spring Boot 가 두 도구를 동등하게 지원하므로 통합 비용 차이가 없다.

## 버린 대안

**Flyway** — 버전 붙은 SQL 파일만 쓰면 되는 단순함이 가장 큰 장점이고 Spring 현장에서 널리
쓰인다. 기각 근거는 Undo 가 유료라는 점이다. 이 프로젝트는 롤백 경로 작성을 규칙으로 두고 있어
도구가 그것을 실행하지 못하면 규칙이 유명무실해진다.

## 트레이드오프

- changelog 문법(`--changeset`, `--rollback`)을 익혀야 한다. SQL 형식이라 부담은 크지 않다.
- Oracle 은 DDL 이 암묵적으로 커밋되므로, 롤백은 트랜잭션 되돌리기가 아니라
  "되돌리는 SQL 을 실행" 하는 방식이다. 파괴적 변경(컬럼 삭제 등)은 되돌려도 데이터가 돌아오지
  않는다. 그런 changeset 은 `--rollback` 주석에 복구 불가임을 명시한다.
- 실패한 마이그레이션은 실패 지점 앞의 changeset 까지 적용된 상태로 남는다.
  검증 결과와 복구 절차는 `docs/RUNBOOK.md` 에 있다.
