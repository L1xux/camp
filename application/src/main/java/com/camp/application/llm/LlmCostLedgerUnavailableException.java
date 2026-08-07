package com.camp.application.llm;

/** 비용 장부를 읽지 못해 AI 질의를 거부한다. 상한 도달과 구분해 사유를 그대로 노출하기 위한 타입이다. */
public class LlmCostLedgerUnavailableException extends RuntimeException {

    public LlmCostLedgerUnavailableException(Throwable cause) {
        super("LLM 비용 장부를 읽지 못해 질의를 거부한다", cause);
    }
}
