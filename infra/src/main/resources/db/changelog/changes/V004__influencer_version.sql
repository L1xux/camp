--liquibase formatted sql

--changeset uijin:V004-01-influencer-version
--comment: 낙관적 잠금 버전. 채널 변경과 성과 수집 배치가 같은 행을 read-modify-write 하므로 갱신 손실을 버전 검사로 막는다. 롤백하면 그동안 쌓인 버전 값은 복구되지 않는다.
ALTER TABLE influencer ADD version NUMBER(19) DEFAULT 0 NOT NULL;
--rollback ALTER TABLE influencer DROP COLUMN version;
