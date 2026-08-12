package com.camp.domain.influencer;

import com.camp.domain.shared.DomainException;

/** 채널 식별자 규칙 위반. */
public class InvalidChannelIdentifierException extends DomainException {

    public InvalidChannelIdentifierException(String reason) {
        super("채널 식별자가 규칙에 맞지 않는다. 사유: " + reason);
    }
}
