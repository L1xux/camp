package com.camp.adapter.web.observability;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** LLM 트레이싱 내보내기 구성. 키가 없으면 내보내기만 꺼지고 기동은 정상이다. */
@Configuration
public class LlmTracingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LlmTracingConfiguration.class);

    public LlmTracingConfiguration(
            @Value("${LANGFUSE_PUBLIC_KEY:}") String publicKey, @Value("${LANGFUSE_SECRET_KEY:}") String secretKey) {
        if (publicKey.isBlank() || secretKey.isBlank()) {
            log.warn("LANGFUSE_PUBLIC_KEY 또는 LANGFUSE_SECRET_KEY 미설정 - trace 가 Langfuse 로 전송되지 않는다");
        }
    }

    /** Observation 자동 구성이 이 필터들을 앱 전역 레지스트리에 건다. */
    @Bean
    RequestIdObservationFilter requestIdObservationFilter() {
        return new RequestIdObservationFilter();
    }

    @Bean
    LlmContentObservationFilter llmContentObservationFilter() {
        return new LlmContentObservationFilter();
    }

    /** 트레이싱 자동 구성이 SpanExporter 빈을 모아 배치 프로세서에 건다. 키가 없으면 빈 자체가 안 뜬다. */
    @Bean
    @ConditionalOnExpression("!'${LANGFUSE_PUBLIC_KEY:}'.isBlank() && !'${LANGFUSE_SECRET_KEY:}'.isBlank()")
    OtlpHttpSpanExporter langfuseSpanExporter(
            @Value("${LANGFUSE_HOST:https://cloud.langfuse.com}") String host,
            @Value("${LANGFUSE_PUBLIC_KEY}") String publicKey,
            @Value("${LANGFUSE_SECRET_KEY}") String secretKey) {
        return buildExporter(host, publicKey, secretKey);
    }

    /** Langfuse OTLP 규격: 경로 /api/public/otel/v1/traces, 인증 Basic base64(public:secret). */
    static OtlpHttpSpanExporter buildExporter(String host, String publicKey, String secretKey) {
        String credentials =
                Base64.getEncoder().encodeToString((publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
        return OtlpHttpSpanExporter.builder()
                .setEndpoint(host + "/api/public/otel/v1/traces")
                .addHeader("Authorization", "Basic " + credentials)
                .build();
    }
}
