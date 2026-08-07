package com.camp.adapter.web.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** span 이 Langfuse 규격(경로, Basic Auth)의 OTLP HTTP 요청으로 나가는지 모의 수집기로 확인한다. */
class OtlpExportContractTest {

    private HttpServer server;
    private final AtomicReference<String> capturedPath = new AtomicReference<>();
    private final AtomicReference<String> capturedAuth = new AtomicReference<>();
    private final AtomicReference<String> capturedContentType = new AtomicReference<>();
    private final CountDownLatch received = new CountDownLatch(1);

    @BeforeEach
    void startCollector() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            received.countDown();
        });
        server.start();
    }

    @AfterEach
    void stopCollector() {
        server.stop(0);
    }

    @Test
    @DisplayName("span 이 Langfuse OTLP 경로로 Basic Auth 를 달고 전송된다")
    void exportsSpansToLangfusePathWithBasicAuth() throws InterruptedException {
        String host = "http://127.0.0.1:" + server.getAddress().getPort();
        OtlpHttpSpanExporter exporter = LlmTracingConfiguration.buildExporter(host, "pk-lf-test", "sk-lf-test");
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        try {
            tracerProvider
                    .get("camp-test")
                    .spanBuilder("contract-span")
                    .startSpan()
                    .end();
            tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);

            assertThat(received.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(capturedPath.get()).isEqualTo("/api/public/otel/v1/traces");
            String expectedAuth = "Basic "
                    + Base64.getEncoder().encodeToString("pk-lf-test:sk-lf-test".getBytes(StandardCharsets.UTF_8));
            assertThat(capturedAuth.get()).isEqualTo(expectedAuth);
            assertThat(capturedContentType.get()).isEqualTo("application/x-protobuf");
        } finally {
            tracerProvider.close();
        }
    }
}
