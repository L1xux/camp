package com.camp.domain.campaign;

import java.util.List;
import java.util.Optional;

/** 캠페인 상태. sealed 이므로 상태를 추가하면 아래 전이표 switch 가 컴파일되지 않는다. */
public sealed interface CampaignState {

    CampaignState PLANNING = new Planning();
    CampaignState RECRUITING = new Recruiting();
    CampaignState IN_EXECUTION = new InExecution();
    CampaignState CLOSED = new Closed();
    CampaignState SETTLED = new Settled();

    List<CampaignState> ALL = List.of(PLANNING, RECRUITING, IN_EXECUTION, CLOSED, SETTLED);

    /** 예외 메시지와 로그에 쓰는 표기. */
    String label();

    /** 전이표. 최종 상태는 비어 있다. */
    static Optional<CampaignState> next(CampaignState current) {
        return switch (current) {
            case Planning ignored -> Optional.of(RECRUITING);
            case Recruiting ignored -> Optional.of(IN_EXECUTION);
            case InExecution ignored -> Optional.of(CLOSED);
            case Closed ignored -> Optional.of(SETTLED);
            case Settled ignored -> Optional.empty();
        };
    }

    record Planning() implements CampaignState {
        @Override
        public String label() {
            return "PLANNING(기획)";
        }
    }

    record Recruiting() implements CampaignState {
        @Override
        public String label() {
            return "RECRUITING(섭외중)";
        }
    }

    record InExecution() implements CampaignState {
        @Override
        public String label() {
            return "IN_EXECUTION(집행중)";
        }
    }

    record Closed() implements CampaignState {
        @Override
        public String label() {
            return "CLOSED(종료)";
        }
    }

    record Settled() implements CampaignState {
        @Override
        public String label() {
            return "SETTLED(결산완료)";
        }
    }
}
