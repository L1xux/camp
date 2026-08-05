package com.camp.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 금액 값 객체. 계약과 예산이 모두 이 타입이라 생성 시점 검증이 뒤의 모든 계산을 지킨다. */
class MoneyTest {

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -5_000_000L})
    @DisplayName("음수와 0원은 생성 시점에 거부한다")
    void rejectsZeroAndNegative(long amount) {
        assertThatThrownBy(() -> Money.won(amount))
                .isInstanceOf(InvalidMoneyException.class)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining(String.valueOf(amount));
    }

    @Test
    @DisplayName("덧셈과 비교 연산이 정확하다")
    void addsAndComparesExactly() {
        Money fourMillion = Money.won(4_000_000);
        Money sixMillion = Money.won(6_000_000);
        Money tenMillion = Money.won(10_000_000);

        assertThat(fourMillion.plus(sixMillion)).isEqualTo(tenMillion);
        assertThat(sixMillion.isGreaterThan(fourMillion)).isTrue();
        assertThat(fourMillion.isGreaterThan(sixMillion)).isFalse();
        assertThat(tenMillion.isGreaterThan(tenMillion)).isFalse();
        assertThat(tenMillion.isGreaterThanOrEqualTo(tenMillion)).isTrue();
        assertThat(fourMillion.isGreaterThanOrEqualTo(sixMillion)).isFalse();
        assertThat(fourMillion.compareTo(sixMillion)).isNegative();

        // 소수점 표기가 달라도 같은 금액이면 같은 값이다. 합계 비교가 표기에 흔들리지 않아야 한다.
        assertThat(new Money(new BigDecimal("5000000.00"))).isEqualTo(Money.won(5_000_000));
    }
}
