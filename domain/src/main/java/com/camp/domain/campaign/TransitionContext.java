package com.camp.domain.campaign;

import java.time.LocalDate;
import java.util.Objects;

/** 캠페인 바깥에서 와야 하는 전이 조건. 계약 건수와 집계 확정 여부는 캠페인이 스스로 알 수 없다. */
public record TransitionContext(
        int activeContractCount, LocalDate today, boolean manualClose, boolean metricsFinalized) {

    public TransitionContext {
        Objects.requireNonNull(today, "today 는 null 일 수 없다");
        if (activeContractCount < 0) {
            throw new IllegalArgumentException("activeContractCount 는 음수일 수 없다: " + activeContractCount);
        }
    }
}
