package com.camp.adapter.web.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLogFormatter;

/**
 * 전 모듈의 로그를 JSON 한 줄로 낸다. application.yml 의 logging.structured.format.console 이 이 클래스를 지목한다.
 */
public class CampJsonLogFormatter implements StructuredLogFormatter<ILoggingEvent> {

    public static final String REQUEST_ID = "request_id";

    // 기동 로그처럼 요청 밖에서 나는 로그도 키는 유지한다. 파서가 필드 존재를 가정할 수 있게.
    private static final String NO_REQUEST = "-";

    private final JsonWriter<ILoggingEvent> writer = JsonWriter.<ILoggingEvent>of(members -> {
                members.add(
                        "timestamp",
                        event -> Instant.ofEpochMilli(event.getTimeStamp()).toString());
                members.add("level", event -> event.getLevel().toString());
                members.add("event", ILoggingEvent::getFormattedMessage);
                members.add(REQUEST_ID, CampJsonLogFormatter::requestId);
                members.add("logger", ILoggingEvent::getLoggerName);
                members.add("thread", ILoggingEvent::getThreadName);
                members.addMapEntries(CampJsonLogFormatter::additionalContext);
                members.add("exception", CampJsonLogFormatter::stackTrace).whenNotNull();
                members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, SensitiveDataMasker::mask));
            })
            .withNewLineAtEnd();

    @Override
    public String format(ILoggingEvent event) {
        return writer.writeToString(event);
    }

    private static String requestId(ILoggingEvent event) {
        return event.getMDCPropertyMap().getOrDefault(REQUEST_ID, NO_REQUEST);
    }

    /** request_id 를 뺀 나머지 MDC. 배치의 실행 ID 와 건 ID 가 여기로 나간다. */
    private static Map<String, String> additionalContext(ILoggingEvent event) {
        Map<String, String> context = new LinkedHashMap<>(event.getMDCPropertyMap());
        context.remove(REQUEST_ID);
        return context;
    }

    private static String stackTrace(ILoggingEvent event) {
        IThrowableProxy throwable = event.getThrowableProxy();
        return throwable == null ? null : ThrowableProxyUtil.asString(throwable);
    }
}
