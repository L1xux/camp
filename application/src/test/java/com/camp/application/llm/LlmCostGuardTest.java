package com.camp.application.llm;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.camp.domain.llm.LlmDailyCostCap;
import com.camp.domain.llm.LlmDailyCostCapReachedException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 상한 도달과 장부 장애에서 AI 질의를 막는지 확인한다. */
class LlmCostGuardTest {

    private static final LlmDailyCostCap CAP = new LlmDailyCostCap(new BigDecimal("2"));

    // UTC 로는 8월 7일, KST 로는 8월 8일인 시각. 아래 테스트가 KST 날짜를 봐야만 통과한다.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-07T16:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate KST_TODAY = LocalDate.of(2026, 8, 8);

    @Test
    @DisplayName("상한 도달 상태면 AI 질의를 차단한다")
    void blocksQueryWhenCapReached() {
        FakeLedger ledger = new FakeLedger();
        ledger.add(KST_TODAY, new BigDecimal("2"));

        assertThatThrownBy(new LlmCostGuard(ledger, CAP, CLOCK)::requireQueryAllowed)
                .isInstanceOf(LlmDailyCostCapReachedException.class);
    }

    @Test
    @DisplayName("상한 미만이면 AI 질의를 허용한다")
    void allowsQueryBelowCap() {
        FakeLedger ledger = new FakeLedger();
        ledger.add(KST_TODAY, new BigDecimal("1.99999999"));

        assertThatCode(new LlmCostGuard(ledger, CAP, CLOCK)::requireQueryAllowed)
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("장부 조회에 실패하면 안전한 쪽인 거부로 끝낸다")
    void deniesQueryWhenLedgerReadFails() {
        assertThatThrownBy(new LlmCostGuard(new BrokenLedger(), CAP, CLOCK)::requireQueryAllowed)
                .isInstanceOf(LlmCostLedgerUnavailableException.class);
    }

    private static final class FakeLedger implements LlmCostLedger {

        private final Map<LocalDate, BigDecimal> totals = new HashMap<>();

        @Override
        public BigDecimal totalSpentOn(LocalDate usageDate) {
            return totals.getOrDefault(usageDate, BigDecimal.ZERO);
        }

        @Override
        public void add(LocalDate usageDate, BigDecimal costUsd) {
            totals.merge(usageDate, costUsd, BigDecimal::add);
        }
    }

    private static final class BrokenLedger implements LlmCostLedger {

        @Override
        public BigDecimal totalSpentOn(LocalDate usageDate) {
            throw new IllegalStateException("DB 연결 실패");
        }

        @Override
        public void add(LocalDate usageDate, BigDecimal costUsd) {
            throw new IllegalStateException("DB 연결 실패");
        }
    }
}
