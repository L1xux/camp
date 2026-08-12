package com.camp.domain.influencer;

import java.util.Objects;

/** 인플루언서가 운영하는 채널 하나. 성과 수집은 이 단위로 붙는다. */
public record InfluencerChannel(
        Platform platform, ChannelIdentifier identifier, FollowerCount followerCount, ChannelStatus status) {

    public InfluencerChannel {
        Objects.requireNonNull(platform, "플랫폼은 null 일 수 없다");
        Objects.requireNonNull(identifier, "채널 식별자는 null 일 수 없다");
        Objects.requireNonNull(followerCount, "팔로워 수는 null 일 수 없다");
        Objects.requireNonNull(status, "상태는 null 일 수 없다");
    }

    public static InfluencerChannel of(Platform platform, String identifier, long followerCount) {
        return new InfluencerChannel(
                platform, ChannelIdentifier.of(identifier), FollowerCount.of(followerCount), ChannelStatus.ACTIVE);
    }

    /** 같은 플랫폼의 같은 식별자면 같은 채널이다. */
    public boolean isSameChannelAs(InfluencerChannel other) {
        return platform == other.platform && identifier.equals(other.identifier);
    }
}
