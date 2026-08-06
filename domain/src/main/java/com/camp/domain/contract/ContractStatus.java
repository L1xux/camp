package com.camp.domain.contract;

/** 계약 상태. 결재 결과에 따라서만 바뀐다. */
public enum ContractStatus {
    PENDING_APPROVAL,
    ACTIVE,
    REJECTED
}
