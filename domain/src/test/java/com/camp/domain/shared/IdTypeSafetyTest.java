package com.camp.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.camp.domain.campaign.CampaignId;
import com.camp.domain.contract.ContractId;
import com.camp.domain.influencer.InfluencerId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 식별자 타입 안전성. 컴파일 차단이 본 방어선이고 이 테스트는 런타임 동등성만 확인한다. */
class IdTypeSafetyTest {

    @Test
    @DisplayName("값이 같아도 다른 타입의 식별자는 같지 않다")
    void differentIdTypesAreNeverEqual() {
        CampaignId campaignId = CampaignId.of(1);
        ContractId contractId = ContractId.of(1);
        InfluencerId influencerId = InfluencerId.of(1);

        // 아래 두 줄은 컴파일되지 않는다. 타입이 다른 식별자는 == 로 비교할 수 없다.
        // boolean sameReference = campaignId == contractId;
        // CampaignId wrong = contractId;

        assertThat(campaignId).isNotEqualTo(contractId);
        assertThat(campaignId).isNotEqualTo(influencerId);
        assertThat(contractId).isNotEqualTo(influencerId);
        assertThat(campaignId).isEqualTo(CampaignId.of(1));
    }
}
