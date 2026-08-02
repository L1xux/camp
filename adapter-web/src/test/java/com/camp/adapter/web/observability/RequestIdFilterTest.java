package com.camp.adapter.web.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 컨트롤러부터 외부 호출까지 같은 request_id 가 흐르는지 확인한다. 픽스처는 테스트 전용이다. */
class RequestIdFilterTest {

    private static final String PROBE_LOGGER_PREFIX = RequestIdFilterTest.class.getName();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilter(new RequestIdFilter())
                .build();
    }

    @Test
    @DisplayName("요청 1건의 컨트롤러, 서비스, 외부 호출 로그가 전부 같은 request_id 를 단다")
    void sameRequestIdAcrossLayers() throws Exception {
        List<ILoggingEvent> events;
        MvcResult result;
        try (CapturedLogs logs = new CapturedLogs()) {
            result = mockMvc.perform(get("/probe")).andReturn();
            events = logs.eventsFrom(PROBE_LOGGER_PREFIX);
        }

        assertThat(events)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("probe.controller", "probe.service", "probe.outbound");

        String responseHeader = result.getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(responseHeader).isNotBlank();
        assertThat(events)
                .allSatisfy(event -> assertThat(event.getMDCPropertyMap())
                        .containsEntry(CampJsonLogFormatter.REQUEST_ID, responseHeader));
    }

    @Test
    @DisplayName("X-Request-Id 헤더를 주면 그 값을 쓴다")
    void usesProvidedHeader() throws Exception {
        List<ILoggingEvent> events;
        MvcResult result;
        try (CapturedLogs logs = new CapturedLogs()) {
            result = mockMvc.perform(get("/probe").header(RequestIdFilter.HEADER, "given-request-id"))
                    .andReturn();
            events = logs.eventsFrom(PROBE_LOGGER_PREFIX);
        }

        assertThat(result.getResponse().getHeader(RequestIdFilter.HEADER)).isEqualTo("given-request-id");
        assertThat(events)
                .allSatisfy(event -> assertThat(event.getMDCPropertyMap())
                        .containsEntry(CampJsonLogFormatter.REQUEST_ID, "given-request-id"));
    }

    @Test
    @DisplayName("헤더가 없으면 UUID 를 생성한다")
    void generatesWhenHeaderMissing() throws Exception {
        MvcResult result = mockMvc.perform(get("/probe")).andReturn();

        String requestId = result.getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(requestId).isNotNull();
        assertThat(UUID.fromString(requestId)).hasToString(requestId);
    }

    @Test
    @DisplayName("로그를 위조할 수 있는 헤더 값은 버리고 새로 생성한다")
    void rejectsMalformedHeader() throws Exception {
        MvcResult result = mockMvc.perform(get("/probe").header(RequestIdFilter.HEADER, "abc\ninjected"))
                .andReturn();

        String requestId = result.getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(requestId).doesNotContain("injected");
        assertThat(UUID.fromString(requestId)).hasToString(requestId);
    }

    @Test
    @DisplayName("요청이 끝나면 MDC 를 비운다")
    void clearsMdcAfterRequest() throws Exception {
        mockMvc.perform(get("/probe")).andReturn();

        assertThat(org.slf4j.MDC.get(CampJsonLogFormatter.REQUEST_ID)).isNull();
    }

    @RestController
    static class ProbeController {

        private static final Logger log = LoggerFactory.getLogger(PROBE_LOGGER_PREFIX + ".ProbeController");

        private final ProbeService service = new ProbeService();

        @GetMapping("/probe")
        String probe() {
            log.info("probe.controller");
            service.handle();
            return "ok";
        }
    }

    static class ProbeService {

        private static final Logger log = LoggerFactory.getLogger(PROBE_LOGGER_PREFIX + ".ProbeService");

        private final ProbeOutboundClient client = new ProbeOutboundClient();

        void handle() {
            log.info("probe.service");
            client.call();
        }
    }

    /** 외부 API 호출 자리. 실제 HTTP 클라이언트는 이슈 #34 의 PR C 에서 붙는다. */
    static class ProbeOutboundClient {

        private static final Logger log = LoggerFactory.getLogger(PROBE_LOGGER_PREFIX + ".ProbeOutboundClient");

        void call() {
            log.info("probe.outbound");
        }
    }
}
