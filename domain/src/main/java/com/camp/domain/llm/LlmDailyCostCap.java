package com.camp.domain.llm;

import java.math.BigDecimal;
import java.util.Objects;

/** LLM 일일 비용 상한. 통화는 USD 이고 KRW 전용인 Money 를 쓰지 않는다. */
public record LlmDailyCostCap(BigDecimal limitUsd) {

    public LlmDailyCostCap {
        Objects.requireNonNull(limitUsd, "limitUsd 는 null 일 수 없다");
    }

    /** 상한과 같아지는 순간 막는다. 상한을 넘긴 뒤에 막으면 마지막 한 건이 상한을 넘겨 나간다. */
    public boolean isReachedBy(BigDecimal spentUsd) {
        return spentUsd.compareTo(limitUsd) >= 0;
    }
}
