package com.camp.domain.campaign;

import com.camp.domain.shared.DomainException;

/** 전이표에 없는 전이 시도. 역방향과 단계 건너뛰기가 여기로 온다. */
public class IllegalCampaignTransitionException extends DomainException {

    public IllegalCampaignTransitionException(CampaignState current, CampaignState attempted) {
        super("허용되지 않는 상태 전이다. 현재: " + current.label() + ", 시도: " + attempted.label());
    }
}
