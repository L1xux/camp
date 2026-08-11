package com.camp.application.llm;

import java.math.BigDecimal;
import java.time.LocalDate;

/** LLM 비용 누적 장부. 구현체는 infra 에 있다. */
public interface LlmCostLedger {

    /** 해당 날짜의 누적 비용. 기록이 없으면 0 이다. */
    BigDecimal totalSpentOn(LocalDate usageDate);

    /** 해당 날짜에 비용을 더한다. 동시에 호출해도 유실되지 않아야 한다. */
    void add(LocalDate usageDate, BigDecimal costUsd);
}
