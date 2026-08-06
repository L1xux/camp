package com.camp.domain.campaign;

import java.time.LocalDate;
import java.util.Objects;

/** 캠페인 집행 기간. 종료일이 시작일보다 앞설 수 없다. */
public record CampaignPeriod(LocalDate start, LocalDate end) {

    public CampaignPeriod {
        Objects.requireNonNull(start, "start 는 null 일 수 없다");
        Objects.requireNonNull(end, "end 는 null 일 수 없다");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("종료일이 시작일보다 앞설 수 없다. " + start + " ~ " + end);
        }
    }

    public boolean hasEndedBy(LocalDate today) {
        return !today.isBefore(end);
    }
}
