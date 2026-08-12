package com.camp.domain.influencer;

import com.camp.domain.shared.DomainException;

/** 이미 등록된 채널을 다시 추가하려는 시도. 채널 식별자는 공개 정보라 메시지에 남긴다. */
public class DuplicateChannelException extends DomainException {

    public DuplicateChannelException(Platform platform, ChannelIdentifier identifier) {
        super("이미 등록된 채널이다. 플랫폼: " + platform + ", 식별자: " + identifier);
    }
}
