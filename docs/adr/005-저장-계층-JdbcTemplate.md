# ADR-005: 인플루언서 애그리거트 저장 계층은 JdbcTemplate 으로 구현한다

- 상태: **채택** (2026-08-19) — 반론 세션을 거쳤고, 반론이 기각한 초안 근거는 버리고 다시 썼다
- 상황: 인플루언서 애그리거트의 저장 계층 기술이 필요하다. influencer 와 influencer_channel 의
  1:N 이고, follower_total 비정규화 컬럼과 soft delete 가 있다. 후보는 JPA(Hibernate),
  Spring Data JDBC, jOOQ, MyBatis, JdbcTemplate.

## 결정의 범위

이 결정은 **애그리거트 쓰기 경로**에만 적용된다. 검색은 애그리거트를 로드하지 않는
전용 조회 SQL 과 전용 DTO 로 간다. 목록 한 페이지를 위해 채널 컬렉션까지 로드하는 것은
어느 기술에서도 오답이므로, 검색 경로는 이 결정의 논거가 아니다.

## 확인한 사실 (2026-08-19)

| 항목 | 확인 내용 |
|---|---|
| ArchUnit 규칙 | domain 의 `jakarta.persistence..` 참조는 테스트 실패다. JPA 를 쓰려면 infra 에 별도 엔티티와 도메인 왕복 매퍼가 필요하다 |
| Spring Data JDBC 의 자식 저장 | 기존 애그리거트를 저장하면 참조 엔티티를 전부 삭제하고 다시 삽입한다. 공식 레퍼런스에 명시 — "it deletes referenced entities, updates the aggregate root, and re-inserts referenced entities" <!-- verified: 2026-08-19 | spring-data-relational 공식 문서 entity-persistence.adoc, domain-driven-design.adoc 대조 --> |
| jOOQ 라이선스 | 오픈소스 에디션은 오픈소스 DB 전용이다. Oracle 은 상용 에디션이 필요하다 — https://www.jooq.org/legal/licensing <!-- verified: 2026-08-19 | jooq.org 라이선스 페이지 대조 --> |
| 유니크 제약 위반 처리 | JdbcTemplate 은 제약 위반 시 해당 문장만 실패하고 트랜잭션이 살아 있어, 같은 트랜잭션에서 충돌한 기존 채널의 소유자를 조회할 수 있다. JPA 는 flush 시점 제약 위반이 트랜잭션을 rollback-only 로 만들어 후속 조회가 불가능하다 <!-- unverified: JPA 쪽 동작은 문서 지식이다. 이 레포에서 실험하지 않았다 -->

## 결정

**JdbcTemplate.** 포트 `InfluencerRepository` 는 application 에, 구현 `JdbcInfluencerRepository` 는
infra 에 둔다.

## 이유

1. 채널 중복 등록의 409 응답에는 기존 리소스 ID 가 실려야 한다. 중복을 먼저 조회하고 삽입하는
   방식은 경합 창이 있어 반드시 제약 위반을 받아서 처리해야 하는데, 위 표에 적었듯 제약 위반
   후 같은 트랜잭션에서 기존 소유자를 조회할 수 있는 쪽은 JdbcTemplate 이다.
2. 대량 시딩(#42)이 수십만 건을 넣는다. `batchUpdate` 로 끝나는 일이, Hibernate 에서는 배치
   크기와 flush 주기를 맞춰야 하고 IDENTITY 키 전략이면 배치가 조용히 꺼진다.
3. ArchUnit 규칙 때문에 JPA 를 써도 별도 엔티티와 왕복 매퍼를 손으로 쓴다. 그때 JPA 가 남기는
   실익은 자식 컬렉션의 변경분 계산인데, 이 애그리거트는 채널 물리 삭제가 없어 변경분 계산이
   "없으면 넣고 있으면 갱신" 두 경우뿐이다.

## 버린 대안

- **Spring Data JDBC** — 애그리거트 단위 저장이라는 개념은 이 도메인과 정확히 맞는다. 기각
  근거는 자식 컬렉션을 삭제 후 재삽입하는 저장 방식이다. 성과 스냅샷(#40)이 채널 행을
  참조하므로 채널 id 가 저장 때마다 바뀌면 이력이 끊긴다.
- **JPA(Hibernate)** — 왕복 매퍼를 손으로 쓰는 순간 남는 실익이 작고, 이유 1 과 2 의 두
  요구사항에서 오히려 불리하다.
- **jOOQ** — 동적 조건 조립을 컴파일 타임으로 옮겨주지만 Oracle 은 상용 라이선스가 필요하다.
- **MyBatis** — 도메인 오염도 라이선스 문제도 없지만, JdbcTemplate 대비 얻는 것이 XML 관리
  비용을 넘지 못한다.

## 트레이드오프와 전제 조건

반론 세션이 짚은 JdbcTemplate 의 비용은 코드량이 아니라 조용히 틀리는 코드의 비중이다.
아래 전제를 지켜 그 비용을 상쇄한다.

1. **낙관적 잠금.** influencer 에 version 컬럼을 둔다 (V004). 채널 변경과 성과 수집 배치(#40)가
   같은 행을 read-modify-write 하므로, 버전 검사 없는 UPDATE 는 갱신 손실을 조용히 만든다.
   저장소의 UPDATE 는 `WHERE id = ? AND version = ?` 이고 영향 행이 0이면 예외다.
   <!-- verified: 2026-08-19 | InfluencerRepositoryIntegrationTest.rejectsStaleVersionOnSave 실제 Oracle 에서 통과 -->
2. **follower_total 계산은 도메인 한 곳에서만.** SQL 로 직접 SUM 하는 경로를 만들지 않는다.
   배치도 애그리거트를 거치고, 인플루언서 단위로 묶어 처리한다.
3. **검색 경로 규율.** platform 조건은 JOIN 이 아니라 EXISTS 로 쓴다. JOIN 은 부모 행을 채널
   수만큼 곱해 페이징을 절단한다. count 쿼리와 데이터 쿼리의 WHERE 는 같은 코드에서 생성하고,
   동적 SQL 의 값은 이름 파라미터로만 넘긴다. 검색 PR 에서 적용한다.
4. **왕복 테스트 유지.** 모든 필드를 채운 애그리거트를 저장하고 다시 읽어 비교하는 테스트가
   컬럼 추가 시 UPDATE 누락과 Oracle 의 빈 문자열 NULL 변환을 잡는 유일한 장치다.
5. **수동 비용 인지.** 컬럼 하나를 추가하면 SELECT 목록, RowMapper, INSERT, UPDATE 네 곳을
   고쳐야 한다. JPA 라면 한 곳이다. 이 비용은 4번 테스트가 잡아주는 것을 전제로 받아들인다.

비활성 채널이 유니크 키를 계속 점유하는 문제는 전역 유니크 유지로 결정했다. 비활성 채널과
충돌하는 409 는 재활성화 API 가 필요하다는 신호이고, 이슈 #36 도 재활성화를 별도 이슈로
분류하고 있어 일관된다.
