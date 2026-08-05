package com.camp.adapter.web.observability;

import io.sentry.EventProcessor;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Sentry 이벤트에 MDC 의 request_id 를 태그로 붙인다. 로그와 Sentry 를 오가며 추적하기 위한 것. */
@Component
public class RequestIdEventProcessor implements EventProcessor {

    @Override
    public SentryEvent process(SentryEvent event, Hint hint) {
        String requestId = MDC.get(CampJsonLogFormatter.REQUEST_ID);
        if (requestId != null) {
            event.setTag(CampJsonLogFormatter.REQUEST_ID, requestId);
        }
        return event;
    }
}
