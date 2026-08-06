package com.camp.domain.campaign;

import com.camp.domain.shared.Money;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Objects;

/** 캠페인 예산과 그 소진 검증. 예약분은 저장하지 않고 계약 상태에서 매번 계산한다. */
public record CampaignBudget(Money total) {

    public CampaignBudget {
        Objects.requireNonNull(total, "total 은 null 일 수 없다");
    }

    /** 예산을 잡아먹고 있는 금액 합계. 반려된 계약은 자동으로 빠진다. */
    public BigDecimal committed(Collection<? extends BudgetReservation> reservations) {
        return reservations.stream()
                .filter(BudgetReservation::reservesBudget)
                .map(reservation -> reservation.amount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 잔여 예산. 예산을 다 쓰면 0 이 되므로 Money 가 아니라 BigDecimal 이다. */
    public BigDecimal remaining(Collection<? extends BudgetReservation> reservations) {
        return total.amount().subtract(committed(reservations));
    }

    public void requireCanReserve(Collection<? extends BudgetReservation> reservations, Money additional) {
        BigDecimal remaining = remaining(reservations);
        if (additional.amount().compareTo(remaining) > 0) {
            throw new BudgetExceededException(remaining, additional);
        }
    }
}
