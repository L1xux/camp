package com.camp.domain.influencer;

/** 인플루언서 식별자. 원시 Long 을 도메인 API 에 노출하지 않아 ID 뒤바뀜이 컴파일 에러가 된다. */
public record InfluencerId(long value) {

    public InfluencerId {
        if (value <= 0) {
            throw new IllegalArgumentException("InfluencerId 는 양수여야 한다. 시도한 값: " + value);
        }
    }

    public static InfluencerId of(long value) {
        return new InfluencerId(value);
    }

    @Override
    public String toString() {
        return "InfluencerId(" + value + ")";
    }
}
