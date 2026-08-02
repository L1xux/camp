package com.camp.adapter.web.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class CampJsonLogFormatterTest {

    private static final Logger log = LoggerFactory.getLogger(CampJsonLogFormatterTest.class);

    private final CampJsonLogFormatter formatter = new CampJsonLogFormatter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("필수 필드 timestamp, level, event, request_id 가 모두 나온다")
    void writesRequiredFields() throws Exception {
        MDC.put(CampJsonLogFormatter.REQUEST_ID, "req-1");

        JsonNode json = formatOne(() -> log.info("campaign.created"));

        assertThat(json.get("timestamp").asText()).isNotEmpty();
        assertThat(json.get("level").asText()).isEqualTo("INFO");
        assertThat(json.get("event").asText()).isEqualTo("campaign.created");
        assertThat(json.get("request_id").asText()).isEqualTo("req-1");
    }

    @Test
    @DisplayName("요청 밖의 로그도 request_id 키를 유지한다")
    void keepsRequestIdKeyOutsideRequest() throws Exception {
        JsonNode json = formatOne(() -> log.info("application.started"));

        assertThat(json.has("request_id")).isTrue();
        assertThat(json.get("request_id").asText()).isEqualTo("-");
    }

    @Test
    @DisplayName("request_id 외의 MDC 값도 필드로 나간다")
    void writesAdditionalMdcEntries() throws Exception {
        MDC.put(CampJsonLogFormatter.REQUEST_ID, "req-2");
        MDC.put("batch_run_id", "run-9");

        JsonNode json = formatOne(() -> log.info("batch.item.processed"));

        assertThat(json.get("request_id").asText()).isEqualTo("req-2");
        assertThat(json.get("batch_run_id").asText()).isEqualTo("run-9");
    }

    @Test
    @DisplayName("예외가 있으면 스택 트레이스가 붙는다")
    void writesStackTrace() throws Exception {
        JsonNode json = formatOne(() -> log.error("contract.approval.failed", new IllegalStateException("boom")));

        assertThat(json.get("exception").asText())
                .contains("IllegalStateException")
                .contains("boom");
    }

    @Test
    @DisplayName("메시지의 전화번호와 이메일이 마스킹되어 출력된다")
    void masksSensitiveDataInOutput() throws Exception {
        String line = formatOneRaw(() -> log.info("문의자 {} 전화 {}", "uijin@example.com", "010-1234-5678"));

        assertThat(line).doesNotContain("uijin@example.com").doesNotContain("010-1234-5678");
        assertThat(objectMapper.readTree(line).get("event").asText())
                .isEqualTo("문의자 u***@example.com 전화 010-****-5678");
    }

    @Test
    @DisplayName("MDC 에 들어온 개인정보도 마스킹된다")
    void masksSensitiveDataInMdc() throws Exception {
        MDC.put("actor_email", "uijin@example.com");

        String line = formatOneRaw(() -> log.info("contract.submitted"));

        assertThat(line).doesNotContain("uijin@example.com");
        assertThat(objectMapper.readTree(line).get("actor_email").asText()).isEqualTo("u***@example.com");
    }

    @Test
    @DisplayName("출력된 로그가 전 건 유효한 JSON 이다")
    void everyLineIsValidJson() {
        MDC.put(CampJsonLogFormatter.REQUEST_ID, "req-3");

        List<String> lines;
        try (CapturedLogs logs = new CapturedLogs()) {
            log.info("plain message");
            log.info("따옴표 \" 와 역슬래시 \\ 와 개행 \n 이 든 메시지");
            log.warn("한글과 이모지 🚀");
            log.error("예외 동반", new IllegalArgumentException("bad \"input\""));
            lines = logs.eventsFrom(CampJsonLogFormatterTest.class.getName()).stream()
                    .map(formatter::format)
                    .toList();
        }

        assertThat(lines).hasSize(4);
        for (String line : lines) {
            assertThatCode(() -> objectMapper.readTree(line))
                    .as("유효한 JSON 이 아니다: %s", line)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("한 이벤트는 개행 하나로 끝난다")
    void endsWithSingleNewLine() {
        String line = formatOneRaw(() -> log.info("single.line"));

        assertThat(line).endsWith("\n");
        assertThat(line.substring(0, line.length() - 1)).doesNotContain("\n");
    }

    private JsonNode formatOne(Runnable logging) throws Exception {
        return objectMapper.readTree(formatOneRaw(logging));
    }

    private String formatOneRaw(Runnable logging) {
        try (CapturedLogs logs = new CapturedLogs()) {
            logging.run();
            List<ILoggingEvent> events = logs.eventsFrom(CampJsonLogFormatterTest.class.getName());
            assertThat(events).hasSize(1);
            return formatter.format(events.get(0));
        }
    }
}
