package com.camp.domain.contract;

import com.camp.domain.shared.DomainException;

/** 사유 없는 반려 시도. 반려 사유는 감사 추적에 남아야 한다. */
public class RejectionReasonRequiredException extends DomainException {

    public RejectionReasonRequiredException(int order) {
        super("반려에는 사유가 필요하다. 단계: " + order);
    }
}
