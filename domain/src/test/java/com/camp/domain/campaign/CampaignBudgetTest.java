package com.camp.domain.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.camp.domain.contract.ApprovalLinePolicy;
import com.camp.domain.contract.Contract;
import com.camp.domain.contract.ContractId;
import com.camp.domain.contract.ContractStatus;
import com.camp.domain.influencer.InfluencerId;
import com.camp.domain.shared.DomainException;
import com.camp.domain.shared.Money;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 예산 불변식. 예약분은 저장하지 않고 계약 상태에서 매번 계산한다. */
class CampaignBudgetTest {

    private static final ApprovalLinePolicy POLICY = new ApprovalLinePolicy();
    private static final CampaignBudget TEN_MILLION = new CampaignBudget(Money.won(10_000_000));

    @Test
    @DisplayName("ACTIVE 600만이 있으면 400만은 상신되고 그보다 크면 거부된다")
    void activeContractsConsumeBudget() {
        Contract active = activeContract(Money.won(6_000_000));

        assertThatCode(() -> submit(2, Money.won(4_000_000), List.of(active))).doesNotThrowAnyException();
        assertThatThrownBy(() -> submit(3, Money.won(4_000_001), List.of(active)))
                .isInstanceOf(BudgetExceededException.class)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("4000000");
    }

    @Test
    @DisplayName("결재 진행 중 600만도 예산을 잡아먹으므로 500만은 거부된다")
    void pendingContractsAlsoReserveBudget() {
        Contract pending = submit(1, Money.won(6_000_000), List.of());
        assertThat(pending.status()).isEqualTo(ContractStatus.PENDING_APPROVAL);

        assertThatThrownBy(() -> submit(2, Money.won(5_000_000), List.of(pending)))
                .isInstanceOf(BudgetExceededException.class);
    }

    @Test
    @DisplayName("진행 중 600만이 반려되면 예약이 풀려 800만이 상신된다")
    void rejectionReleasesReservation() {
        Contract pending = submit(1, Money.won(6_000_000), List.of());
        pending.reject(1, "조건 미달");

        assertThat(TEN_MILLION.committed(List.of(pending))).isEqualByComparingTo("0");
        assertThatCode(() -> submit(2, Money.won(8_000_000), List.of(pending))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("재상신으로 금액을 400만에서 600만으로 바꾸면 예약분이 새 금액으로 잡힌다")
    void resubmitReservesNewAmount() {
        Contract contract = submit(1, Money.won(4_000_000), List.of());
        contract.reject(1, "금액 재협의");

        contract.resubmit(Money.won(6_000_000), TEN_MILLION, List.of(contract), POLICY);

        assertThat(TEN_MILLION.committed(List.of(contract))).isEqualByComparingTo("6000000");
        assertThat(TEN_MILLION.remaining(List.of(contract))).isEqualByComparingTo("4000000");
        assertThatThrownBy(() -> submit(2, Money.won(4_000_001), List.of(contract)))
                .isInstanceOf(BudgetExceededException.class);
    }

    @Test
    @DisplayName("합계가 예산과 정확히 같으면 허용된다")
    void allowsExactlyEqualToBudget() {
        Contract active = activeContract(Money.won(6_000_000));

        Contract exact = submit(2, Money.won(4_000_000), List.of(active));

        assertThat(TEN_MILLION.committed(List.of(active, exact))).isEqualByComparingTo("10000000");
        assertThat(TEN_MILLION.remaining(List.of(active, exact))).isEqualByComparingTo("0");
    }

    private static Contract activeContract(Money amount) {
        Contract contract = submit(1, amount, List.of());
        for (int order = 1; order <= contract.currentApprovalLine().steps().size(); order++) {
            contract.approve(order);
        }
        assertThat(contract.status()).isEqualTo(ContractStatus.ACTIVE);
        return contract;
    }

    private static Contract submit(long id, Money amount, List<Contract> existing) {
        return Contract.submit(
                ContractId.of(id), CampaignId.of(1), InfluencerId.of(id), amount, TEN_MILLION, existing, POLICY);
    }
}
