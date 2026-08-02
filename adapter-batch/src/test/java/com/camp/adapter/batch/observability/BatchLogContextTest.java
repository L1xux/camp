package com.camp.adapter.batch.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class BatchLogContextTest {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(BatchLogContextTest.class);

    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        root.setLevel(Level.INFO);
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        root.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    @Test
    @DisplayName("배치 처리 로그에 실행 ID 와 건 ID 가 함께 들어간다")
    void includesRunIdAndItemId() {
        BatchLogContext.inRun("run-2026-08-02", () -> {
            log.info("batch.started");
            for (String itemId : List.of("item-1", "item-2")) {
                BatchLogContext.inItem(itemId, () -> log.info("batch.item.processed"));
            }
        });

        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(3);

        assertThat(events.get(0).getMDCPropertyMap())
                .containsEntry(BatchLogContext.RUN_ID, "run-2026-08-02")
                .doesNotContainKey(BatchLogContext.ITEM_ID);
        assertThat(events.get(1).getMDCPropertyMap())
                .containsEntry(BatchLogContext.RUN_ID, "run-2026-08-02")
                .containsEntry(BatchLogContext.ITEM_ID, "item-1");
        assertThat(events.get(2).getMDCPropertyMap())
                .containsEntry(BatchLogContext.RUN_ID, "run-2026-08-02")
                .containsEntry(BatchLogContext.ITEM_ID, "item-2");
    }

    @Test
    @DisplayName("건 처리가 실패해도 MDC 는 정리된다")
    void clearsItemIdOnFailure() {
        BatchLogContext.inRun("run-1", () -> {
            assertThatThrownBy(() -> BatchLogContext.inItem("item-1", () -> {
                        throw new IllegalStateException("수집 실패");
                    }))
                    .isInstanceOf(IllegalStateException.class);

            log.info("batch.item.skipped");
        });

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getMDCPropertyMap())
                .containsEntry(BatchLogContext.RUN_ID, "run-1")
                .doesNotContainKey(BatchLogContext.ITEM_ID);
    }

    @Test
    @DisplayName("배치가 끝나면 실행 ID 가 남지 않는다")
    void clearsRunIdAfterRun() {
        BatchLogContext.inRun("run-1", () -> {});

        assertThat(MDC.get(BatchLogContext.RUN_ID)).isNull();
    }
}
