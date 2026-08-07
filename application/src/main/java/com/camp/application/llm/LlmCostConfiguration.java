package com.camp.application.llm;

import com.camp.domain.llm.LlmDailyCostCap;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 설정값을 도메인 타입으로 바꿔 올린다. */
@Configuration
public class LlmCostConfiguration {

    @Bean
    LlmDailyCostCap llmDailyCostCap(@Value("${camp.llm.daily-cost-cap-usd}") BigDecimal capUsd) {
        return new LlmDailyCostCap(capUsd);
    }
}
