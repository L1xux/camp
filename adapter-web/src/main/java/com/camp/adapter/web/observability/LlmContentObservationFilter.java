package com.camp.adapter.web.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationContext;

/** 생성 span 에 입력과 출력을 싣는다. Spring AI 1.0 부터 내용 캡처가 로깅으로 바뀌어 직접 붙여야 한다. */
public class LlmContentObservationFilter implements ObservationFilter {

    // Langfuse 가 입력과 출력으로 인식하는 속성 키. https://langfuse.com/integrations/frameworks/spring-ai
    static final String PROMPT_KEY = "gen_ai.prompt";
    static final String COMPLETION_KEY = "gen_ai.completion";

    @Override
    public Observation.Context map(Observation.Context context) {
        if (!(context instanceof ChatModelObservationContext chat)) {
            return context;
        }
        chat.addHighCardinalityKeyValue(
                KeyValue.of(PROMPT_KEY, chat.getRequest().getContents()));
        ChatResponse response = chat.getResponse();
        if (response != null) {
            String completion = response.getResults().stream()
                    .map(generation -> generation.getOutput().getText())
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n"));
            chat.addHighCardinalityKeyValue(KeyValue.of(COMPLETION_KEY, completion));
        }
        return chat;
    }
}
