package com.camp.domain.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.camp.domain.shared.DomainException;
import com.camp.domain.shared.Money;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 캠페인 상태 전이. 전이표와 전이 조건을 나누어 검증한다. */
class CampaignStateMachineTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    /** 조건을 전부 충족하는 컨텍스트. 전이표 검증에서 조건이 원인이 되지 않게 한다. */
    private static final TransitionContext SATISFYING = new TransitionContext(1, END, false, true);

    @Test
    @DisplayName("허용 전이 4건이 조건 충족 상태에서 각각 성공한다")
    void allowsFourTransitionsWhenConditionsAreMet() {
        Campaign campaign = readyToRecruit();

        campaign.transitionTo(CampaignState.RECRUITING, SATISFYING);
        assertThat(campaign.state()).isEqualTo(CampaignState.RECRUITING);

        campaign.transitionTo(CampaignState.IN_EXECUTION, SATISFYING);
        assertThat(campaign.state()).isEqualTo(CampaignState.IN_EXECUTION);

        campaign.transitionTo(CampaignState.CLOSED, SATISFYING);
        assertThat(campaign.state()).isEqualTo(CampaignState.CLOSED);

        campaign.transitionTo(CampaignState.SETTLED, SATISFYING);
        assertThat(campaign.state()).isEqualTo(CampaignState.SETTLED);
    }

    @Test
    @DisplayName("전이표에 없는 조합 16건이 각각 예외다")
    void rejectsEveryIllegalTransition() {
        int checked = 0;

        for (CampaignState from : CampaignState.ALL) {
            for (CampaignState to : CampaignState.ALL) {
                if (from.equals(to) || isAllowed(from, to)) {
                    continue;
                }
                Campaign campaign = campaignIn(from);
                assertThatThrownBy(() -> campaign.transitionTo(to, SATISFYING))
                        .isInstanceOf(IllegalCampaignTransitionException.class)
                        .isInstanceOf(DomainException.class)
                        .hasMessageContaining(from.label())
                        .hasMessageContaining(to.label());
                checked++;
            }
        }

        assertThat(checked).isEqualTo(16);
    }

    @Test
    @DisplayName("유효 계약 0건이면 RECRUITING 에서 IN_EXECUTION 으로 갈 수 없다")
    void requiresAtLeastOneActiveContract() {
        Campaign campaign = campaignIn(CampaignState.RECRUITING);
        TransitionContext noContract = new TransitionContext(0, END, false, true);

        assertThatThrownBy(() -> campaign.transitionTo(CampaignState.IN_EXECUTION, noContract))
                .isInstanceOf(CampaignConditionNotMetException.class)
                .hasMessageContaining("유효 계약이 0건이다");
        assertThat(campaign.state()).isEqualTo(CampaignState.RECRUITING);
    }

    @Test
    @DisplayName("예산이 설정되지 않으면 PLANNING 에서 RECRUITING 으로 갈 수 없다")
    void requiresBudgetBeforeRecruiting() {
        Campaign campaign = Campaign.planning(CampaignId.of(1));
        campaign.definePeriod(new CampaignPeriod(START, END));

        assertThatThrownBy(() -> campaign.transitionTo(CampaignState.RECRUITING, SATISFYING))
                .isInstanceOf(CampaignConditionNotMetException.class)
                .hasMessageContaining("예산과 기간이 설정되지 않았다");
        assertThat(campaign.state()).isEqualTo(CampaignState.PLANNING);
    }

    @Test
    @DisplayName("SETTLED 에서는 어떤 전이도 할 수 없다")
    void settledIsFinal() {
        for (CampaignState to : CampaignState.ALL) {
            Campaign campaign = campaignIn(CampaignState.SETTLED);

            assertThatThrownBy(() -> campaign.transitionTo(to, SATISFYING))
                    .isInstanceOf(IllegalCampaignTransitionException.class)
                    .hasMessageContaining(CampaignState.SETTLED.label());
            assertThat(campaign.state()).isEqualTo(CampaignState.SETTLED);
        }
    }

    private static boolean isAllowed(CampaignState from, CampaignState to) {
        return CampaignState.next(from).filter(to::equals).isPresent();
    }

    /** 전이를 실제로 밟아 목표 상태의 캠페인을 만든다. 테스트 전용 생성자를 두지 않기 위한 것. */
    private static Campaign campaignIn(CampaignState state) {
        Campaign campaign = readyToRecruit();
        if (campaign.state().equals(state)) {
            return campaign;
        }
        for (CampaignState next : CampaignState.ALL) {
            campaign.transitionTo(CampaignState.next(campaign.state()).orElseThrow(), SATISFYING);
            if (campaign.state().equals(state)) {
                return campaign;
            }
        }
        throw new IllegalArgumentException("도달할 수 없는 상태: " + state.label());
    }

    private static Campaign readyToRecruit() {
        Campaign campaign = Campaign.planning(CampaignId.of(1));
        campaign.defineBudget(Money.won(10_000_000));
        campaign.definePeriod(new CampaignPeriod(START, END));
        return campaign;
    }
}
