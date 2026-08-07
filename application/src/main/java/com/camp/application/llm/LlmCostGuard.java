package com.camp.application.llm;

import com.camp.application.shared.UseCase;
import com.camp.domain.llm.LlmDailyCostCap;
import com.camp.domain.llm.LlmDailyCostCapReachedException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** AI 질의를 열어줄지 판단한다. 운영 API 는 이 판단을 거치지 않는다. */
@UseCase
public class LlmCostGuard {

    /** 상한은 KST 자정에 풀린다. 날짜별로 장부 행이 나뉘는 것이 곧 리셋이다. */
    public static final ZoneId RESET_ZONE = ZoneId.of("Asia/Seoul");

    private final LlmCostLedger ledger;
    private final LlmDailyCostCap cap;
    private final Clock clock;

    public LlmCostGuard(LlmCostLedger ledger, LlmDailyCostCap cap, Clock clock) {
        this.ledger = ledger;
        this.cap = cap;
        this.clock = clock;
    }

    /** 상한에 도달했거나 장부를 읽지 못하면 예외를 던진다. */
    public void requireQueryAllowed() {
        BigDecimal spent;
        try {
            spent = ledger.totalSpentOn(today());
        } catch (RuntimeException e) {
            // 얼마 썼는지 모르는 상태에서 통과시키면 상한이 없는 것과 같다.
            throw new LlmCostLedgerUnavailableException(e);
        }
        if (cap.isReachedBy(spent)) {
            throw new LlmDailyCostCapReachedException(spent, cap.limitUsd());
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(RESET_ZONE));
    }
}
