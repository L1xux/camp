package com.camp.adapter.web.observability;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.integration.Slf4jThreadLocalAccessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

/** 스레드를 넘길 때 MDC 를 함께 넘긴다. 빈으로 올리면 Spring Boot 가 자동 구성하는 실행기가 이 데코레이터를 쓴다. */
@Configuration
public class ObservabilityConfiguration {

    /** MDC 접근자는 자동 등록되지 않는다. 등록을 빠뜨리면 스레드를 넘길 때 request_id 가 조용히 사라진다. */
    @Bean
    TaskDecorator contextPropagatingTaskDecorator() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new Slf4jThreadLocalAccessor());
        return new ContextPropagatingTaskDecorator();
    }
}
