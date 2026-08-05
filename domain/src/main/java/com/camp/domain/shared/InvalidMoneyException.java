package com.camp.domain.shared;

import java.math.BigDecimal;

/** 0원 이하 금액 생성 시도. 계약과 예산 모두 양수만 의미가 있다. */
public class InvalidMoneyException extends DomainException {

    public InvalidMoneyException(BigDecimal amount) {
        super("금액은 0보다 커야 한다. 시도한 값: " + amount.toPlainString());
    }
}
