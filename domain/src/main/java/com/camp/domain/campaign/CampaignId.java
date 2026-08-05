package com.camp.domain.campaign;

/** 캠페인 식별자. 원시 Long 을 도메인 API 에 노출하지 않아 ID 뒤바뀜이 컴파일 에러가 된다. */
public record CampaignId(long value) {

    public CampaignId {
        if (value <= 0) {
            throw new IllegalArgumentException("CampaignId 는 양수여야 한다. 시도한 값: " + value);
        }
    }

    public static CampaignId of(long value) {
        return new CampaignId(value);
    }

    @Override
    public String toString() {
        return "CampaignId(" + value + ")";
    }
}
