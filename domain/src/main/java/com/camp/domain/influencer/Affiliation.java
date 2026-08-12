package com.camp.domain.influencer;

/** 소속 정보. FREELANCER 는 소속사명을 갖지 않는다. */
public record Affiliation(AffiliationType type, String agencyName) {

    public Affiliation {
        if (type == null) {
            throw new IllegalArgumentException("소속 형태는 null 일 수 없다");
        }
        if (type == AffiliationType.AGENCY) {
            if (agencyName == null || agencyName.isBlank()) {
                throw new AgencyNameRequiredException();
            }
            agencyName = agencyName.strip();
        } else {
            agencyName = null;
        }
    }

    public static Affiliation freelancer() {
        return new Affiliation(AffiliationType.FREELANCER, null);
    }

    public static Affiliation agency(String agencyName) {
        return new Affiliation(AffiliationType.AGENCY, agencyName);
    }
}
