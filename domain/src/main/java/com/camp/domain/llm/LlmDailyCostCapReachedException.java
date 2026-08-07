package com.camp.domain.llm;

import com.camp.domain.shared.DomainException;
import java.math.BigDecimal;

/** 일일 비용 상한에 도달해 AI 질의를 거부한다. 운영 API 는 이 예외와 무관하다. */
public class LlmDailyCostCapReachedException extends DomainException {

    private final BigDecimal spentUsd;
    private final BigDecimal limitUsd;

    public LlmDailyCostCapReachedException(BigDecimal spentUsd, BigDecimal limitUsd) {
        super("오늘 LLM 비용이 상한에 도달했다. 누적 %s USD, 상한 %s USD".formatted(spentUsd, limitUsd));
        this.spentUsd = spentUsd;
        this.limitUsd = limitUsd;
    }

    public BigDecimal spentUsd() {
        return spentUsd;
    }

    public BigDecimal limitUsd() {
        return limitUsd;
    }
}
