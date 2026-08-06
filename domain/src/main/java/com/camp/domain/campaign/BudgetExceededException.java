package com.camp.domain.campaign;

import com.camp.domain.shared.DomainException;
import com.camp.domain.shared.Money;
import java.math.BigDecimal;

/** 예약 가능한 잔여 예산을 넘는 상신 시도. */
public class BudgetExceededException extends DomainException {

    public BudgetExceededException(BigDecimal remaining, Money requested) {
        super("예산을 초과한다. 잔여 예산: " + remaining.toPlainString() + "원, 요청 금액: " + requested);
    }
}
