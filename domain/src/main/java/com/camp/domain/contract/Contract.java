package com.camp.domain.contract;

import com.camp.domain.campaign.BudgetReservation;
import com.camp.domain.campaign.CampaignBudget;
import com.camp.domain.campaign.CampaignId;
import com.camp.domain.influencer.InfluencerId;
import com.camp.domain.shared.Money;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** 계약. 결재선을 통과해야 유효해지고, 재상신해도 이전 결재 이력은 남는다. */
public final class Contract implements BudgetReservation {

    private final ContractId id;
    private final CampaignId campaignId;
    private final InfluencerId influencerId;
    private final List<ApprovalLine> approvalLines = new ArrayList<>();

    private Money amount;
    private ContractStatus status;

    private Contract(ContractId id, CampaignId campaignId, InfluencerId influencerId, Money amount) {
        this.id = Objects.requireNonNull(id, "id 는 null 일 수 없다");
        this.campaignId = Objects.requireNonNull(campaignId, "campaignId 는 null 일 수 없다");
        this.influencerId = Objects.requireNonNull(influencerId, "influencerId 는 null 일 수 없다");
        this.amount = Objects.requireNonNull(amount, "amount 는 null 일 수 없다");
    }

    /** campaignContracts 는 같은 캠페인의 다른 계약이다. 예산 예약분을 여기서 계산한다. */
    public static Contract submit(
            ContractId id,
            CampaignId campaignId,
            InfluencerId influencerId,
            Money amount,
            CampaignBudget budget,
            Collection<Contract> campaignContracts,
            ApprovalLinePolicy policy) {
        budget.requireCanReserve(campaignContracts, amount);

        Contract contract = new Contract(id, campaignId, influencerId, amount);
        contract.startNewApprovalLine(policy);
        return contract;
    }

    public void approve(int stepOrder) {
        currentApprovalLine().approve(stepOrder);
        if (currentApprovalLine().isFullyApproved()) {
            status = ContractStatus.ACTIVE;
        }
    }

    public void reject(int stepOrder, String reason) {
        currentApprovalLine().reject(stepOrder, reason);
        status = ContractStatus.REJECTED;
    }

    /** 반려된 계약만 재상신한다. 금액이 바뀌면 결재선도 새 금액 기준으로 다시 만든다. */
    public void resubmit(
            Money newAmount, CampaignBudget budget, Collection<Contract> campaignContracts, ApprovalLinePolicy policy) {
        if (status != ContractStatus.REJECTED) {
            throw new ContractNotResubmittableException(id, status);
        }
        Objects.requireNonNull(newAmount, "newAmount 는 null 일 수 없다");
        budget.requireCanReserve(campaignContracts, newAmount);

        amount = newAmount;
        startNewApprovalLine(policy);
    }

    /** 새 결재선을 이력 끝에 덧붙인다. 이전 결재선은 지우거나 덮어쓰지 않는다. */
    private void startNewApprovalLine(ApprovalLinePolicy policy) {
        approvalLines.add(ApprovalLine.of(policy.lineFor(amount)));
        status = ContractStatus.PENDING_APPROVAL;
    }

    public ApprovalLine currentApprovalLine() {
        return approvalLines.get(approvalLines.size() - 1);
    }

    public List<ApprovalLine> approvalHistory() {
        return List.copyOf(approvalLines);
    }

    @Override
    public Money amount() {
        return amount;
    }

    @Override
    public boolean reservesBudget() {
        return status == ContractStatus.ACTIVE || status == ContractStatus.PENDING_APPROVAL;
    }

    public ContractId id() {
        return id;
    }

    public CampaignId campaignId() {
        return campaignId;
    }

    public InfluencerId influencerId() {
        return influencerId;
    }

    public ContractStatus status() {
        return status;
    }
}
