package com.camp.domain.campaign;

import com.camp.domain.shared.Money;
import java.util.Objects;
import java.util.Optional;

/** 캠페인. 상태 전이가 전이표와 조건을 모두 통과해야만 일어난다. */
public final class Campaign {

    private final CampaignId id;

    private Money budget;
    private CampaignPeriod period;
    private CampaignState state;

    private Campaign(CampaignId id) {
        this.id = Objects.requireNonNull(id, "id 는 null 일 수 없다");
        this.state = CampaignState.PLANNING;
    }

    public static Campaign planning(CampaignId id) {
        return new Campaign(id);
    }

    public void defineBudget(Money budget) {
        this.budget = Objects.requireNonNull(budget, "budget 은 null 일 수 없다");
    }

    public void definePeriod(CampaignPeriod period) {
        this.period = Objects.requireNonNull(period, "period 는 null 일 수 없다");
    }

    public void transitionTo(CampaignState target, TransitionContext context) {
        Objects.requireNonNull(target, "target 은 null 일 수 없다");
        Objects.requireNonNull(context, "context 는 null 일 수 없다");

        CampaignState allowed =
                CampaignState.next(state).orElseThrow(() -> new IllegalCampaignTransitionException(state, target));
        if (!allowed.equals(target)) {
            throw new IllegalCampaignTransitionException(state, target);
        }
        requireConditionSatisfied(target, context);
        state = target;
    }

    /** 전이 조건 검사. 상태를 추가하면 이 switch 가 컴파일되지 않아 누락을 막는다. */
    private void requireConditionSatisfied(CampaignState target, TransitionContext context) {
        switch (state) {
            case CampaignState.Planning ignored -> {
                if (budget == null || period == null) {
                    throw new CampaignConditionNotMetException(state, target, "예산과 기간이 설정되지 않았다");
                }
            }
            case CampaignState.Recruiting ignored -> {
                if (context.activeContractCount() < 1) {
                    throw new CampaignConditionNotMetException(state, target, "유효 계약이 0건이다");
                }
            }
            case CampaignState.InExecution ignored -> {
                if (!context.manualClose() && !period.hasEndedBy(context.today())) {
                    throw new CampaignConditionNotMetException(state, target, "종료일이 도래하지 않았고 수동 종료도 아니다");
                }
            }
            case CampaignState.Closed ignored -> {
                if (!context.metricsFinalized()) {
                    throw new CampaignConditionNotMetException(state, target, "성과 집계가 확정되지 않았다");
                }
            }
            // 전이표가 비어 있어 위에서 이미 거부된다.
            case CampaignState.Settled ignored -> throw new IllegalCampaignTransitionException(state, target);
        }
    }

    public CampaignId id() {
        return id;
    }

    public CampaignState state() {
        return state;
    }

    public Optional<Money> budget() {
        return Optional.ofNullable(budget);
    }

    public Optional<CampaignPeriod> period() {
        return Optional.ofNullable(period);
    }
}
