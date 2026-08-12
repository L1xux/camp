package com.camp.domain.influencer;

/** 채널 상태. 성과 스냅샷이 채널을 참조하므로 물리 삭제 대신 INACTIVE 로 바꾼다. */
public enum ChannelStatus {
    ACTIVE,
    INACTIVE
}
