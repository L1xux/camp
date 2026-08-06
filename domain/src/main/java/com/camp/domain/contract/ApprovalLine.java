package com.camp.domain.contract;

import java.util.ArrayList;
import java.util.List;

/** 순차 결재선. 선행 단계가 승인되기 전에는 후행 단계를 처리할 수 없다. */
public final class ApprovalLine {

    private final List<ApprovalStep> steps;

    private ApprovalLine(List<ApprovalStep> steps) {
        this.steps = steps;
    }

    static ApprovalLine of(List<ApproverRole> roles) {
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("결재선은 비어 있을 수 없다");
        }
        List<ApprovalStep> created = new ArrayList<>();
        for (int index = 0; index < roles.size(); index++) {
            created.add(new ApprovalStep(roles.get(index), index + 1));
        }
        return new ApprovalLine(List.copyOf(created));
    }

    void approve(int stepOrder) {
        ApprovalStep step = stepAt(stepOrder);
        requirePrecedingApproved(stepOrder);
        step.approve();
    }

    void reject(int stepOrder, String reason) {
        ApprovalStep step = stepAt(stepOrder);
        requirePrecedingApproved(stepOrder);
        step.reject(reason);
    }

    public boolean isFullyApproved() {
        return steps.stream().allMatch(ApprovalStep::isApproved);
    }

    public boolean isRejected() {
        return steps.stream().anyMatch(ApprovalStep::isRejected);
    }

    public List<ApproverRole> roles() {
        return steps.stream().map(ApprovalStep::role).toList();
    }

    public List<ApprovalStep> steps() {
        return steps;
    }

    private ApprovalStep stepAt(int stepOrder) {
        if (stepOrder < 1 || stepOrder > steps.size()) {
            throw new IllegalArgumentException("결재 단계 범위를 벗어났다: " + stepOrder + ", 단계 수: " + steps.size());
        }
        return steps.get(stepOrder - 1);
    }

    private void requirePrecedingApproved(int stepOrder) {
        for (ApprovalStep preceding : steps.subList(0, stepOrder - 1)) {
            if (!preceding.isApproved()) {
                throw new ApprovalSequenceException(stepOrder, preceding.order(), preceding.status());
            }
        }
    }
}
