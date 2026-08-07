package com.camp.domain.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 일일 비용 상한 판정 규칙. */
class LlmDailyCostCapTest {

    private final LlmDailyCostCap cap = new LlmDailyCostCap(new BigDecimal("2"));

    @ParameterizedTest(name = "[{index}] 누적 {0} USD 면 차단 여부는 {1}")
    @CsvSource({
        "0, false",
        "1.99999999, false",
        // 자릿수가 달라도 값이 같으면 같은 판정이어야 한다.
        "1.99, false",
        "2, true",
        "2.00, true",
        "2.00000001, true",
        "10, true",
    })
    @DisplayName("누적 비용이 상한에 도달하면 차단한다")
    void blocksWhenSpendingReachesLimit(String spent, boolean blocked) {
        assertThat(cap.isReachedBy(new BigDecimal(spent))).isEqualTo(blocked);
    }
}
