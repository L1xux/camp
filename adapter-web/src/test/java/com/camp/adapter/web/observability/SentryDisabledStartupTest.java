package com.camp.adapter.web.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.sentry.spring.boot.jakarta.SentryAutoConfiguration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** DSN 이 비어 있어도 기동이 막히지 않아야 한다. 관측 도구 부재가 앱을 죽이면 안 된다. */
class SentryDisabledStartupTest {

    @Test
    @DisplayName("DSN 미설정으로 기동하면 정상 기동하고 경고 로그 1건을 남긴다")
    void startsWithoutDsnAndWarnsOnce() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withPropertyValues("sentry.dsn=")
                .withConfiguration(AutoConfigurations.of(SentryAutoConfiguration.class))
                .withUserConfiguration(SentryConfiguration.class);

        try (CapturedLogs logs = new CapturedLogs()) {
            runner.run(context -> {
                assertThat(context).hasNotFailed();

                List<ILoggingEvent> warns = logs.eventsFrom(SentryConfiguration.class.getName()).stream()
                        .filter(event -> event.getLevel() == Level.WARN)
                        .toList();
                assertThat(warns).hasSize(1);
                assertThat(warns.get(0).getFormattedMessage()).contains("SENTRY_DSN 미설정");
            });
        }
    }
}
