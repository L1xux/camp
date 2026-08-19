package com.camp.domain.influencer;

import com.camp.domain.shared.DomainException;

/** 이름 길이 규칙 위반. 이름 자체는 개인정보라 메시지에 넣지 않고 길이만 남긴다. */
public class InvalidInfluencerNameException extends DomainException {

    public InvalidInfluencerNameException(String reason) {
        super("인플루언서 이름이 규칙에 맞지 않는다. 사유: " + reason);
    }
}
