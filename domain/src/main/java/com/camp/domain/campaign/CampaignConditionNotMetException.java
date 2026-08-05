package com.camp.domain.campaign;

import com.camp.domain.shared.DomainException;

/** 전이표에는 있으나 조건을 못 채운 전이 시도. */
public class CampaignConditionNotMetException extends DomainException {

    public CampaignConditionNotMetException(CampaignState current, CampaignState attempted, String reason) {
        super("전이 조건을 충족하지 못했다. 현재: " + current.label() + ", 시도: " + attempted.label() + ", 사유: " + reason);
    }
}
