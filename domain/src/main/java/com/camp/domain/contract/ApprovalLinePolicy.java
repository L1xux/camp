package com.camp.domain.contract;

import com.camp.domain.shared.Money;
import java.util.List;

/** 금액 구간별 결재선 정책. 구간을 바꾸려면 아래 표만 고친다. */
public final class ApprovalLinePolicy {

    private record Bracket(Money minimumInclusive, List<ApproverRole> line) {}

    // 금액 내림차순. 먼저 걸리는 구간이 결재선이 된다.
    private static final List<Bracket> TABLE = List.of(
            new Bracket(Money.won(5_000_000), List.of(ApproverRole.TEAM_LEAD, ApproverRole.DIVISION_HEAD)),
            new Bracket(Money.won(1), List.of(ApproverRole.TEAM_LEAD)));

    public List<ApproverRole> lineFor(Money amount) {
        return TABLE.stream()
                .filter(bracket -> amount.isGreaterThanOrEqualTo(bracket.minimumInclusive()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("구간표가 금액을 포함하지 않는다: " + amount))
                .line();
    }
}
