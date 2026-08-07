package com.camp.adapter.web.observability;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/** 운영과 같은 필터 구성으로 Observation 을 OTel span 으로 바꿔 메모리에 모은다. */
final class TracingTestHarness implements AutoCloseable {

    final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    final SdkTracerProvider tracerProvider;
    final ObservationRegistry registry;

    TracingTestHarness() {
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OtelTracer tracer = new OtelTracer(tracerProvider.get("camp-test"), new OtelCurrentTraceContext(), event -> {});
        registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new DefaultTracingObservationHandler(tracer))
                .observationFilter(new RequestIdObservationFilter())
                .observationFilter(new LlmContentObservationFilter());
    }

    List<SpanData> finishedSpans() {
        return exporter.getFinishedSpanItems();
    }

    /** 실제 모델 구현체가 하는 그대로 Spring AI 관측 규약을 태워 모의 호출을 계측한다. */
    static ChatResponse observeMockLlmCall(ObservationRegistry registry, String promptText, String completionText) {
        Prompt prompt =
                new Prompt(promptText, ChatOptions.builder().model("mock-model").build());
        ChatModelObservationContext context = ChatModelObservationContext.builder()
                .prompt(prompt)
                .provider("mock")
                .build();
        return ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
                .observation(null, new DefaultChatModelObservationConvention(), () -> context, registry)
                .observe(() -> {
                    sleepBriefly();
                    ChatResponse response = ChatResponse.builder()
                            .generations(List.of(new Generation(new AssistantMessage(completionText))))
                            .metadata(ChatResponseMetadata.builder()
                                    .usage(new DefaultUsage(10, 20))
                                    .build())
                            .build();
                    context.setResponse(response);
                    return response;
                });
    }

    /** 지연시간 기록을 확인하려면 시작과 끝 시각이 같으면 안 된다. */
    private static void sleepBriefly() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        tracerProvider.close();
    }
}
