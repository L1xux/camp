package com.camp.domain.influencer;

/** 인플루언서 상태. 물리 삭제 대신 INACTIVE 로 바꿔 과거 계약의 참조를 지킨다. */
public enum InfluencerStatus {
    ACTIVE,
    INACTIVE
}
