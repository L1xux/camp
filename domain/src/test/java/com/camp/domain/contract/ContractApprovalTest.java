package com.camp.domain.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.camp.domain.campaign.CampaignBudget;
import com.camp.domain.campaign.CampaignId;
import com.camp.domain.influencer.InfluencerId;
import com.camp.domain.shared.DomainException;
import com.camp.domain.shared.Money;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** 결재선 결정과 순차 결재, 재상신 규칙. */
class ContractApprovalTest {

    private static final ApprovalLinePolicy POLICY = new ApprovalLinePolicy();

    /** 결재 규칙만 보기 위해 예산은 넉넉히 둔다. 예산 검증은 CampaignBudgetTest 가 맡는다. */
    private static final CampaignBudget AMPLE_BUDGET = new CampaignBudget(Money.won(1_000_000_000));

    @Test
    @DisplayName("금액 4,999,999 원의 결재선은 팀장 1단계다")
    void singleStepBelowThreshold() {
        Contract contract = submit(Money.won(4_999_999));

        assertThat(contract.currentApprovalLine().roles()).containsExactly(ApproverRole.TEAM_LEAD);
    }

    @Test
    @DisplayName("금액 5,000,000 원의 결재선은 팀장, 본부장 2단계다")
    void twoStepsAtThreshold() {
        Contract contract = submit(Money.won(5_000_000));

        assertThat(contract.currentApprovalLine().roles())
                .containsExactly(ApproverRole.TEAM_LEAD, ApproverRole.DIVISION_HEAD);
    }

    @Test
    @DisplayName("1단계 승인 후 2단계까지 승인하면 계약이 ACTIVE 가 된다")
    void becomesActiveAfterAllStepsApproved() {
        Contract contract = submit(Money.won(5_000_000));

        contract.approve(1);
        assertThat(contract.status()).isEqualTo(ContractStatus.PENDING_APPROVAL);

        contract.approve(2);
        assertThat(contract.status()).isEqualTo(ContractStatus.ACTIVE);
    }

    @Test
    @DisplayName("1단계 승인 전에는 2단계를 처리할 수 없다")
    void cannotProcessLaterStepFirst() {
        Contract contract = submit(Money.won(5_000_000));

        assertThatThrownBy(() -> contract.approve(2))
                .isInstanceOf(ApprovalSequenceException.class)
                .isInstanceOf(DomainException.class);
        assertThat(contract.status()).isEqualTo(ContractStatus.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("1단계가 반려되면 계약이 REJECTED 가 되고 2단계는 처리할 수 없다")
    void rejectionBlocksRemainingSteps() {
        Contract contract = submit(Money.won(5_000_000));

        contract.reject(1, "예산 근거 부족");

        assertThat(contract.status()).isEqualTo(ContractStatus.REJECTED);
        assertThat(contract.currentApprovalLine().steps().get(0).rejectionReason())
                .contains("예산 근거 부족");
        assertThatThrownBy(() -> contract.approve(2)).isInstanceOf(ApprovalSequenceException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("반려 사유가 비어 있으면 반려할 수 없다")
    void rejectionRequiresReason(String reason) {
        Contract contract = submit(Money.won(5_000_000));

        assertThatThrownBy(() -> contract.reject(1, reason)).isInstanceOf(RejectionReasonRequiredException.class);
        assertThat(contract.status()).isEqualTo(ContractStatus.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("반려된 계약을 재상신하면 결재선이 새로 생기고 이전 이력이 남는다")
    void resubmitCreatesNewLineAndKeepsHistory() {
        Contract contract = submit(Money.won(4_000_000));
        contract.reject(1, "금액 재협의 필요");
        ApprovalLine rejectedLine = contract.currentApprovalLine();

        contract.resubmit(Money.won(6_000_000), AMPLE_BUDGET, List.of(contract), POLICY);

        assertThat(contract.approvalHistory()).hasSize(2);
        assertThat(contract.approvalHistory().get(0)).isSameAs(rejectedLine);
        assertThat(rejectedLine.isRejected()).isTrue();
        assertThat(contract.currentApprovalLine()).isNotSameAs(rejectedLine);
        assertThat(contract.currentApprovalLine().roles())
                .containsExactly(ApproverRole.TEAM_LEAD, ApproverRole.DIVISION_HEAD);
        assertThat(contract.status()).isEqualTo(ContractStatus.PENDING_APPROVAL);
        assertThat(contract.amount()).isEqualTo(Money.won(6_000_000));
    }

    @Test
    @DisplayName("ACTIVE 계약은 재상신할 수 없다")
    void activeContractCannotBeResubmitted() {
        Contract contract = submit(Money.won(4_000_000));
        contract.approve(1);

        assertThatThrownBy(() -> contract.resubmit(Money.won(3_000_000), AMPLE_BUDGET, List.of(contract), POLICY))
                .isInstanceOf(ContractNotResubmittableException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    @DisplayName("같은 단계를 두 번 승인하면 두 번째가 예외다")
    void cannotApproveSameStepTwice() {
        Contract contract = submit(Money.won(5_000_000));

        contract.approve(1);

        assertThatThrownBy(() -> contract.approve(1)).isInstanceOf(ApprovalStepAlreadyProcessedException.class);
    }

    private static Contract submit(Money amount) {
        return Contract.submit(
                ContractId.of(1), CampaignId.of(1), InfluencerId.of(1), amount, AMPLE_BUDGET, List.of(), POLICY);
    }
}
