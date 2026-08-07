package com.camp.adapter.web.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.slf4j.MDC;

/** MDC 의 request_id 를 모든 span 에 붙인다. 로그, Sentry, trace 를 잇는 연결 고리다. */
public class RequestIdObservationFilter implements ObservationFilter {

    @Override
    public Observation.Context map(Observation.Context context) {
        String requestId = MDC.get(CampJsonLogFormatter.REQUEST_ID);
        if (requestId != null) {
            context.addHighCardinalityKeyValue(KeyValue.of(CampJsonLogFormatter.REQUEST_ID, requestId));
        }
        return context;
    }
}
