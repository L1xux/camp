# 운영 절차서

이 문서는 절차만 담는다. 배경과 판단 근거는 ADR 에 있다.
현재는 DB 마이그레이션과 계정 권한만 다룬다. 기동과 재기동, 시드 재실행, 배포 롤백,
시크릿 폐기와 재발급, 백업과 복구는 이슈 #47 에서 추가한다.

로컬 기동과 DB 초기화 방법은 [README](../README.md#실행-방법) 에 있다.

## DB 마이그레이션

### 새 마이그레이션 추가

1. `infra/src/main/resources/db/changelog/changes/` 에 `V00N__설명.sql` 파일을 만든다.
2. `db.changelog-master.yaml` 아래에 `include` 를 추가한다. 기존 항목은 건드리지 않는다.
3. 각 changeset 에 `--rollback` 을 함께 쓴다. 되돌릴 수 없는 변경이면 그 사실을 주석으로 남긴다.

```sql
--liquibase formatted sql

--changeset uijin:V002-01-add-column
ALTER TABLE brand ADD (memo VARCHAR2(500));
--rollback ALTER TABLE brand DROP COLUMN memo;

--changeset uijin:V002-02-drop-column
--comment: 되돌려도 데이터는 복구되지 않는다. 롤백은 컬럼 구조만 되살린다.
ALTER TABLE brand DROP COLUMN legacy_code;
--rollback ALTER TABLE brand ADD (legacy_code VARCHAR2(20));
```

### 이미 적용된 마이그레이션은 고치지 않는다

파일을 고치면 체크섬이 달라져 기동이 거부된다.

```
liquibase.exception.ValidationFailedException: Validation Failed:
     1 changesets check sum
          db/changelog/changes/V001__baseline.sql::V001-01-create-brand::uijin
          was: 9:e5de422fe7d53bf8714a92c94ba1c56c but is now: 9:fff268cb0b36be61a1dc603391710879
```

**조치**: 파일을 원래대로 되돌리고 새 버전 파일을 추가한다.
로컬 개발 중이라 이력을 버려도 되면 DB 를 초기화하고 다시 적용한다.

### 마이그레이션이 중간에 실패했을 때

Oracle 은 DDL 앞뒤로 암묵적 커밋을 하므로, **실패 지점 앞의 changeset 은 적용된 채로 남는다.**
실패한 changeset 자체는 이력에 기록되지 않는다.

일부러 깨진 changeset 을 넣어 확인한 결과다 (V003 의 changeset 2개 중 두 번째에서 실패).

```
liquibase.ui        : Running Changeset: db/changelog/changes/V003__broken.sql::V003-01-ok::uijin
liquibase.changelog : ChangeSet db/changelog/changes/V003__broken.sql::V003-01-ok::uijin ran successfully in 11ms
liquibase.ui        : Running Changeset: db/changelog/changes/V003__broken.sql::V003-02-broken::uijin
liquibase.changelog : ChangeSet db/changelog/changes/V003__broken.sql::V003-02-broken::uijin encountered an exception.
liquibase.exception.DatabaseException: ORA-00903: 테이블명이 부적합합니다
 [Failed SQL: (903) CREATE TABLE 123invalid (id NUMBER(19))]
```

기동은 `BeanCreationException` 으로 실패하고, 이력 테이블에는 실패 앞의 changeset 까지 남는다.

```
ID			       ORDEREXECUTED EXECTYPE
------------------------------ ------------- ----------
V001-01-create-brand			   1 EXECUTED
V001-02-brand-code-unique		   2 EXECUTED
V002-01-probe				   3 EXECUTED
V003-01-ok				   4 EXECUTED
```
<!-- verified: 2026-08-02 | V003-02 에 CREATE TABLE 123invalid 를 넣고 bootRun, 이후 databasechangelog 조회 -->

**조치 순서**

1. 이력 테이블에서 어디까지 적용됐는지 확인한다.

   ```bash
   docker exec -i camp-oracle sqlplus -s camp/$DB_PASSWORD@//localhost:1521/FREEPDB1 <<'SQL'
   SELECT id, orderexecuted, exectype FROM databasechangelog ORDER BY orderexecuted;
   EXIT
   SQL
   ```

2. 실패한 changeset 의 SQL 을 고친다. 아직 이력에 없으므로 파일 수정이 허용된다.
   (성공한 changeset 은 고치면 안 된다. 체크섬 검증에 걸린다.)
3. 앱을 다시 띄우면 실패했던 changeset 부터 이어서 적용된다. 앞의 changeset 은 다시 돌지 않는다.

   ```
   liquibase.ui        : Running Changeset: db/changelog/changes/V003__broken.sql::V003-02-broken::uijin
   liquibase.changelog : ChangeSet db/changelog/changes/V003__broken.sql::V003-02-broken::uijin ran successfully in 20ms
   ```
   <!-- verified: 2026-08-02 | V003-02 의 SQL 을 고치고 bootRun -> V003-02 만 실행, 헬스 UP -->

4. DB 가 어중간한 상태로 남았고 로컬이면, DB 를 초기화하고 처음부터 적용하는 것이 빠르다.

### 잠금이 풀리지 않을 때

마이그레이션 도중 프로세스가 강제 종료되면 `DATABASECHANGELOGLOCK` 이 잠긴 채 남는다.
다음 기동이 잠금 대기에서 멈춘다.

```sql
UPDATE databasechangeloglock SET locked = 0, lockgranted = NULL, lockedby = NULL WHERE id = 1;
COMMIT;
```

Oracle 23ai 에서 `locked` 는 `BOOLEAN` 컬럼이고 `0` 과 `1` 이 `FALSE`, `TRUE` 로 들어간다.
잠긴 상태를 만들어 위 UPDATE 로 푸는 것까지 확인했다.

```
	 ID LOCKED      LOCKGRANTED
---------- ----------- ------------------------------
LOCKEDBY
--------------------------------------------------
	  1 FALSE
```
<!-- verified: 2026-08-02 | UPDATE ... SET locked = 1 로 잠근 뒤 위 SQL 실행, SELECT 로 확인 -->

## 계정과 권한

애플리케이션은 `camp` 계정으로만 접속한다. `SYS`, `SYSTEM` 으로 접속하지 않는다.
`camp` 는 `DB_DEVELOPER_ROLE` 만 가지며 시스템 권한과 타 스키마 객체 권한이 없다.

권한 확인:

```sql
SELECT granted_role FROM user_role_privs;
SELECT privilege FROM user_sys_privs;
SELECT owner, table_name, privilege FROM user_tab_privs WHERE grantee = 'CAMP' AND owner <> 'CAMP';
```
