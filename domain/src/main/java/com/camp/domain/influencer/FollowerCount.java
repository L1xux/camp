package com.camp.domain.influencer;

/** 채널 팔로워 수. 0 이상. */
public record FollowerCount(long value) {

    public FollowerCount {
        if (value < 0) {
            throw new InvalidFollowerCountException(value);
        }
    }

    public static FollowerCount of(long value) {
        return new FollowerCount(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
