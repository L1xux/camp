package com.camp.domain.contract;

import java.util.Objects;
import java.util.Optional;

/** 결재선의 한 단계. 한 번 처리되면 다시 처리할 수 없다. */
public final class ApprovalStep {

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }

    private final ApproverRole role;
    private final int order;

    private Status status = Status.PENDING;
    private String rejectionReason;

    ApprovalStep(ApproverRole role, int order) {
        this.role = Objects.requireNonNull(role, "role 은 null 일 수 없다");
        if (order < 1) {
            throw new IllegalArgumentException("order 는 1 이상이어야 한다: " + order);
        }
        this.order = order;
    }

    void approve() {
        requireNotProcessed();
        status = Status.APPROVED;
    }

    void reject(String reason) {
        requireNotProcessed();
        if (reason == null || reason.isBlank()) {
            throw new RejectionReasonRequiredException(order);
        }
        status = Status.REJECTED;
        rejectionReason = reason;
    }

    private void requireNotProcessed() {
        if (status != Status.PENDING) {
            throw new ApprovalStepAlreadyProcessedException(order, status);
        }
    }

    public boolean isApproved() {
        return status == Status.APPROVED;
    }

    public boolean isRejected() {
        return status == Status.REJECTED;
    }

    public ApproverRole role() {
        return role;
    }

    public int order() {
        return order;
    }

    public Status status() {
        return status;
    }

    public Optional<String> rejectionReason() {
        return Optional.ofNullable(rejectionReason);
    }
}
