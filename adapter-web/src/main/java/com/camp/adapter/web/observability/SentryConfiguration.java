package com.camp.adapter.web.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/** DSN 없이 기동하면 수집만 꺼진다. 그 사실을 기동 로그에 한 번 남긴다. */
@Configuration
public class SentryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SentryConfiguration.class);

    public SentryConfiguration(@Value("${sentry.dsn:}") String dsn) {
        if (dsn.isBlank()) {
            log.warn("SENTRY_DSN 미설정 - 미처리 예외가 Sentry 로 수집되지 않는다");
        }
    }
}
