package com.camp.domain.influencer;

/** 채널 식별자. 인스타그램 핸들 또는 유튜브 채널 ID 다. */
public record ChannelIdentifier(String value) {

    public static final int MAX_LENGTH = 100;

    public ChannelIdentifier {
        if (value == null || value.isBlank()) {
            throw new InvalidChannelIdentifierException("비어 있다");
        }
        value = value.strip();
        if (value.length() > MAX_LENGTH) {
            throw new InvalidChannelIdentifierException("최대 " + MAX_LENGTH + " 자인데 " + value.length() + " 자다");
        }
    }

    public static ChannelIdentifier of(String value) {
        return new ChannelIdentifier(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
