package com.camp.domain.contract;

/** 결재자 역할. 결재선은 이 역할의 순서 있는 목록이다. */
public enum ApproverRole {
    TEAM_LEAD("팀장"),
    DIVISION_HEAD("본부장");

    private final String label;

    ApproverRole(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
