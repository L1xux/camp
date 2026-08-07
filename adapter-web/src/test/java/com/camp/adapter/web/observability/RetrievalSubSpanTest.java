package com.camp.adapter.web.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.observation.DefaultVectorStoreObservationConvention;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationDocumentation;

/** RAG 검색이 생성의 하위 span 으로 분리되어 검색이 느린지 생성이 느린지 구분되는지 확인한다. */
class RetrievalSubSpanTest {

    @Test
    @DisplayName("retrieval 포함 호출 → 하위 span 분리 확인")
    void separatesRetrievalAsChildSpan() {
        try (TracingTestHarness harness = new TracingTestHarness()) {
            Prompt prompt =
                    new Prompt("질문", ChatOptions.builder().model("mock-model").build());
            ChatModelObservationContext chatContext = ChatModelObservationContext.builder()
                    .prompt(prompt)
                    .provider("mock")
                    .build();
            ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
                    .observation(null, new DefaultChatModelObservationConvention(), () -> chatContext, harness.registry)
                    .observe(() -> observeRetrieval(harness));

            List<SpanData> spans = harness.finishedSpans();
            assertThat(spans).hasSize(2);

            SpanData retrieval = spans.get(0);
            SpanData generation = spans.get(1);
            assertThat(retrieval.getName()).contains("query");
            assertThat(retrieval.getTraceId()).isEqualTo(generation.getTraceId());
            assertThat(retrieval.getParentSpanId()).isEqualTo(generation.getSpanId());
        }
    }

    /** 실제 VectorStore 구현체가 하는 그대로 검색을 계측한다. */
    private void observeRetrieval(TracingTestHarness harness) {
        VectorStoreObservationContext context = VectorStoreObservationContext.builder(
                        "oracle", VectorStoreObservationContext.Operation.QUERY)
                .build();
        Observation observation = VectorStoreObservationDocumentation.AI_VECTOR_STORE.observation(
                null, new DefaultVectorStoreObservationConvention(), () -> context, harness.registry);
        observation.observe(() -> {});
    }
}
