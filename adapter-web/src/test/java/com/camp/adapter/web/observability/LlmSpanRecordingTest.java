package com.camp.adapter.web.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** 모의 LLM 호출 1건이 span 으로 남고 토큰, 지연, request_id 가 기록되는지 확인한다. */
class LlmSpanRecordingTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("모의 LLM 호출 1건 → trace 에 토큰, 지연, request_id 기록")
    void recordsTokensLatencyAndRequestId() {
        MDC.put(CampJsonLogFormatter.REQUEST_ID, "llm-trace-req-1");

        try (TracingTestHarness harness = new TracingTestHarness()) {
            TracingTestHarness.observeMockLlmCall(harness.registry, "질문", "답변");

            assertThat(harness.finishedSpans()).hasSize(1);
            SpanData span = harness.finishedSpans().get(0);

            assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.usage.input_tokens")))
                    .isEqualTo("10");
            assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.usage.output_tokens")))
                    .isEqualTo("20");
            assertThat(span.getEndEpochNanos() - span.getStartEpochNanos()).isPositive();
            assertThat(span.getAttributes().get(AttributeKey.stringKey(CampJsonLogFormatter.REQUEST_ID)))
                    .isEqualTo("llm-trace-req-1");
        }
    }

    @Test
    @DisplayName("생성 span 에 입력과 출력 내용이 Langfuse 가 인식하는 속성 키로 실린다")
    void recordsPromptAndCompletionContent() {
        try (TracingTestHarness harness = new TracingTestHarness()) {
            TracingTestHarness.observeMockLlmCall(harness.registry, "이번 시즌 ROI 상위 5명은?", "다음과 같습니다.");

            SpanData span = harness.finishedSpans().get(0);
            assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.prompt")))
                    .contains("이번 시즌 ROI 상위 5명은?");
            assertThat(span.getAttributes().get(AttributeKey.stringKey("gen_ai.completion")))
                    .contains("다음과 같습니다.");
        }
    }
}
