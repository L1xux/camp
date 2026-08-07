--liquibase formatted sql

--changeset uijin:V002-01-create-llm-cost-daily
--comment: LLM 일일 비용 누적. KST 날짜마다 행이 나뉘므로 자정이 지나면 새 행에서 다시 쌓인다.
CREATE TABLE llm_cost_daily (
    usage_date     DATE         PRIMARY KEY,
    total_cost_usd NUMBER(12,8) DEFAULT 0 NOT NULL,
    updated_at     TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL
);
--rollback DROP TABLE llm_cost_daily;
