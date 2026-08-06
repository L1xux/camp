package com.camp.domain.campaign;

import com.camp.domain.shared.Money;

/** 예산을 잡아먹는 대상. 계약이 이 역할을 한다. */
public interface BudgetReservation {

    Money amount();

    /** 결재 진행 중인 건도 승인되면 예산을 소비하므로 예약으로 본다. */
    boolean reservesBudget();
}
