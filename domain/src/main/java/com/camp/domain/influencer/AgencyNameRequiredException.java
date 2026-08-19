package com.camp.domain.influencer;

import com.camp.domain.shared.DomainException;

/** 소속 형태가 AGENCY 인데 소속사명이 없는 경우. */
public class AgencyNameRequiredException extends DomainException {

    public AgencyNameRequiredException() {
        super("소속 형태가 " + AffiliationType.AGENCY + " 이면 소속사명이 필요하다");
    }
}
