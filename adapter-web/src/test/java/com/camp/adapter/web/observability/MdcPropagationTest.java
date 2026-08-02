package com.camp.adapter.web.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.integration.Slf4jThreadLocalAccessor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

/** 스레드가 바뀌는 지점마다 request_id 가 남는지 확인한다. */
class MdcPropagationTest {

    private final TaskDecorator decorator = new ContextPropagatingTaskDecorator();

    @BeforeAll
    static void registerMdcAccessor() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new Slf4jThreadLocalAccessor());
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("데코레이터가 없으면 가상 스레드에서 request_id 가 사라진다")
    void requestIdIsLostWithoutDecorator() throws Exception {
        MDC.put(CampJsonLogFormatter.REQUEST_ID, "req-1");

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> observed = executor.submit(MdcPropagationTest::currentRequestId);
            assertThat(observed.get()).isNull();
        }
    }

    @Test
    @DisplayName("가상 스레드로 넘겨도 request_id 가 유지된다")
    void keepsRequestIdOnVirtualThread() throws Exception {
        MDC.put(CampJsonLogFormatter.REQUEST_ID, "req-2");

        AtomicReference<String> observed = new AtomicReference<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(decorator.decorate(() -> observed.set(currentRequestId())))
                    .get();
        }

        assertThat(observed.get()).isEqualTo("req-2");
    }

    @Test
    @DisplayName("비동기 스레드 풀로 넘겨도 request_id 가 유지된다")
    void keepsRequestIdOnPooledThread() throws Exception {
        MDC.put(CampJsonLogFormatter.REQUEST_ID, "req-3");

        AtomicReference<String> observed = new AtomicReference<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(1)) {
            executor.submit(decorator.decorate(() -> observed.set(currentRequestId())))
                    .get();
        }

        assertThat(observed.get()).isEqualTo("req-3");
    }

    @Test
    @DisplayName("재사용되는 풀 스레드에 이전 작업의 request_id 가 남지 않는다")
    void doesNotLeakRequestIdBetweenTasks() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(1)) {
            MDC.put(CampJsonLogFormatter.REQUEST_ID, "req-4");
            executor.submit(decorator.decorate(() -> {})).get();

            MDC.clear();
            AtomicReference<String> observed = new AtomicReference<>();
            executor.submit(decorator.decorate(() -> observed.set(currentRequestId())))
                    .get();

            assertThat(observed.get()).isNull();
        }
    }

    @Test
    @DisplayName("배치 워커가 쓰는 SimpleAsyncTaskExecutor 에서도 유지된다")
    void keepsRequestIdOnSimpleAsyncTaskExecutor() throws Exception {
        MDC.put(CampJsonLogFormatter.REQUEST_ID, "req-5");

        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setVirtualThreads(true);
        executor.setTaskDecorator(decorator);

        AtomicReference<String> observed = new AtomicReference<>();
        executor.submit(() -> observed.set(currentRequestId())).get();
        executor.close();

        assertThat(observed.get()).isEqualTo("req-5");
    }

    @Test
    @DisplayName("설정이 올라가면 Spring Boot 가 자동 구성한 실행기에 데코레이터가 적용된다")
    void bootAppliesDecoratorToAutoConfiguredExecutor() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
                .withUserConfiguration(ObservabilityConfiguration.class)
                .withPropertyValues("spring.threads.virtual.enabled=true")
                .run(context -> {
                    AsyncTaskExecutor executor = context.getBean(AsyncTaskExecutor.class);
                    MDC.put(CampJsonLogFormatter.REQUEST_ID, "req-6");

                    AtomicReference<String> observed = new AtomicReference<>();
                    executor.submit(() -> observed.set(currentRequestId())).get();

                    assertThat(observed.get()).isEqualTo("req-6");
                });
    }

    private static String currentRequestId() {
        return MDC.get(CampJsonLogFormatter.REQUEST_ID);
    }
}
