package com.camp.domain.influencer;

/** 인플루언서 이름. 1~50 자. */
public record InfluencerName(String value) {

    public static final int MAX_LENGTH = 50;

    public InfluencerName {
        if (value == null || value.isBlank()) {
            throw new InvalidInfluencerNameException("비어 있다");
        }
        value = value.strip();
        if (value.length() > MAX_LENGTH) {
            throw new InvalidInfluencerNameException("최대 " + MAX_LENGTH + " 자인데 " + value.length() + " 자다");
        }
    }

    public static InfluencerName of(String value) {
        return new InfluencerName(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
