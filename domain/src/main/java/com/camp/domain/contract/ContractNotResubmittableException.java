package com.camp.domain.contract;

import com.camp.domain.shared.DomainException;

/** 반려되지 않은 계약의 재상신 시도. */
public class ContractNotResubmittableException extends DomainException {

    public ContractNotResubmittableException(ContractId id, ContractStatus status) {
        super("반려된 계약만 재상신할 수 있다. 계약: " + id + ", 상태: " + status);
    }
}
