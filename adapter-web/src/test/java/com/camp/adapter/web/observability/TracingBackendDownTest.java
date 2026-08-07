package com.camp.adapter.web.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;

/** 트레이싱 백엔드가 죽어도 LLM 호출은 정상 진행되고, 계측 실패는 자체 로그로만 남는지 확인한다. */
class TracingBackendDownTest {

    @Test
    @DisplayName("트레이싱 백엔드 다운 → LLM 호출은 정상 진행, 계측 실패만 자체 로그")
    void llmCallSucceedsWhenExporterCannotConnect() throws IOException {
        int closedPort = findClosedPort();
        OtlpHttpSpanExporter exporter =
                LlmTracingConfiguration.buildExporter("http://127.0.0.1:" + closedPort, "pk-test", "sk-test");
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .build();
        OtelTracer tracer = new OtelTracer(tracerProvider.get("camp-test"), new OtelCurrentTraceContext(), event -> {});
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new DefaultTracingObservationHandler(tracer))
                .observationFilter(new RequestIdObservationFilter())
                .observationFilter(new LlmContentObservationFilter());

        List<LogRecord> exporterLogs = new CopyOnWriteArrayList<>();
        Logger otelLogger = Logger.getLogger("io.opentelemetry");
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                exporterLogs.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        otelLogger.addHandler(capture);
        try {
            ChatResponse response = TracingTestHarness.observeMockLlmCall(registry, "질문", "답변");

            assertThat(response.getResult().getOutput().getText()).isEqualTo("답변");

            // forceFlush 는 큐만 비우면 성공을 돌려주므로 내보내기 실패는 자체 로그로 확인한다.
            tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);
            assertThat(exporterLogs)
                    .as("계측 실패가 자체 로그로 남아야 한다")
                    .anyMatch(record -> record.getLevel().intValue() >= Level.WARNING.intValue()
                            && String.valueOf(record.getMessage()).contains("export"));
        } finally {
            otelLogger.removeHandler(capture);
            tracerProvider.close();
        }
    }

    /** 바인딩 없이 닫아 연결이 거부되는 포트를 얻는다. */
    private int findClosedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
