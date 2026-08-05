package com.camp.adapter.web.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.sentry.ScopesAdapter;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.spring.jakarta.SentryExceptionResolver;
import io.sentry.spring.jakarta.tracing.SpringMvcTransactionNameProvider;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 미처리 예외가 Sentry 이벤트로 잡히는 경로를 확인한다. beforeSend 가 null 을 돌려주므로 네트워크 전송은 없다. */
class SentryCaptureTest {

    private final List<SentryEvent> captured = new CopyOnWriteArrayList<>();

    private MockMvc mockMvc;

    @BeforeEach
    void initSentry() {
        Sentry.init(options -> {
            options.setDsn("http://publicKey@localhost:9/1");
            options.addEventProcessor(new RequestIdEventProcessor());
            options.setBeforeSend((event, hint) -> {
                captured.add(event);
                return null;
            });
        });
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .addFilter(new RequestIdFilter())
                .setHandlerExceptionResolvers(new SentryExceptionResolver(
                        ScopesAdapter.getInstance(), new SpringMvcTransactionNameProvider(), 1))
                .build();
    }

    @AfterEach
    void closeSentry() {
        Sentry.close();
    }

    @Test
    @DisplayName("의도적 미처리 예외가 Sentry 이벤트로 잡히고 request_id 태그가 붙는다")
    void capturesUnhandledExceptionWithRequestIdTag() {
        assertThatThrownBy(() -> mockMvc.perform(get("/boom").header(RequestIdFilter.HEADER, "sentry-test-request")))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(captured).hasSize(1);
        SentryEvent event = captured.get(0);
        assertThat(event.getTags()).containsEntry(CampJsonLogFormatter.REQUEST_ID, "sentry-test-request");
        assertThat(event.getThrowable()).isInstanceOf(IllegalStateException.class);
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("intentional-unhandled");
        }
    }
}
