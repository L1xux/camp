package com.camp.adapter.batch.observability;

import org.slf4j.MDC;

/** 배치 로그에 실행 단위 ID 와 처리 건 ID 를 넣는다. 잡 구현은 이슈 #40 에서 이 클래스를 쓴다. */
public final class BatchLogContext {

    public static final String RUN_ID = "batch_run_id";
    public static final String ITEM_ID = "batch_item_id";

    private BatchLogContext() {}

    /** 배치 실행 한 번을 감싼다. runId 는 실행 1회를 식별한다. */
    public static void inRun(String runId, Runnable body) {
        MDC.put(RUN_ID, runId);
        try {
            body.run();
        } finally {
            MDC.remove(RUN_ID);
        }
    }

    /** 처리 건 하나를 감싼다. 예외를 삼키지 않으므로 건별 실패 처리는 호출 쪽 책임이다. */
    public static void inItem(String itemId, Runnable body) {
        MDC.put(ITEM_ID, itemId);
        try {
            body.run();
        } finally {
            MDC.remove(ITEM_ID);
        }
    }
}
