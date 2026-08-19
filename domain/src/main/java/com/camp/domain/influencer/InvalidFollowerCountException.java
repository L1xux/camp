package com.camp.domain.influencer;

import com.camp.domain.shared.DomainException;

/** 음수 팔로워 수 생성 시도. */
public class InvalidFollowerCountException extends DomainException {

    public InvalidFollowerCountException(long value) {
        super("팔로워 수는 0 이상이어야 한다. 시도한 값: " + value);
    }
}
