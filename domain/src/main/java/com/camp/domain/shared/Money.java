package com.camp.domain.shared;

import java.math.BigDecimal;
import java.util.Objects;

/** KRW 단일 통화 금액. 계약 금액과 예산이 모두 이 타입이다. */
public record Money(BigDecimal amount) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "amount 는 null 일 수 없다");
        if (amount.signum() <= 0) {
            throw new InvalidMoneyException(amount);
        }
        // 1 과 1.00 을 같은 값으로 다루기 위해 원 단위로 맞춘다.
        amount = amount.setScale(0, java.math.RoundingMode.UNNECESSARY);
    }

    public static Money won(long amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        return compareTo(other) >= 0;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + "원";
    }
}
