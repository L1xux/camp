package com.camp.adapter.web.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/** 테스트 동안 루트 로거에 붙어 로그 이벤트를 모은다. */
final class CapturedLogs implements AutoCloseable {

    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    CapturedLogs() {
        root.setLevel(Level.INFO);
        appender.start();
        root.addAppender(appender);
    }

    List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    /** 테스트 픽스처가 낸 로그만 고른다. 프레임워크 로그가 섞이면 단언이 흔들린다. */
    List<ILoggingEvent> eventsFrom(String loggerNamePrefix) {
        return events().stream()
                .filter(event -> event.getLoggerName().startsWith(loggerNamePrefix))
                .toList();
    }

    @Override
    public void close() {
        root.detachAppender(appender);
        appender.stop();
    }
}
