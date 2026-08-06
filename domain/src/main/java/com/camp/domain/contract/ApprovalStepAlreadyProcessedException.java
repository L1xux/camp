package com.camp.domain.contract;

import com.camp.domain.shared.DomainException;

/** 이미 처리된 단계의 재처리 시도. 같은 단계를 두 번 승인하는 것을 막는다. */
public class ApprovalStepAlreadyProcessedException extends DomainException {

    public ApprovalStepAlreadyProcessedException(int order, ApprovalStep.Status status) {
        super("이미 처리된 결재 단계다. 단계: " + order + ", 상태: " + status);
    }
}
