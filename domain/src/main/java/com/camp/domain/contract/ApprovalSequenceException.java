package com.camp.domain.contract;

import com.camp.domain.shared.DomainException;

/** 선행 단계가 승인되지 않은 상태에서 후행 단계를 처리하려 한 경우. */
public class ApprovalSequenceException extends DomainException {

    public ApprovalSequenceException(int attemptedOrder, int blockingOrder, ApprovalStep.Status blockingStatus) {
        super("선행 단계가 승인되지 않았다. 시도한 단계: " + attemptedOrder + ", 대기 중인 단계: " + blockingOrder + "(" + blockingStatus
                + ")");
    }
}
