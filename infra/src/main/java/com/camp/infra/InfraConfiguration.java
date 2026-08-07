package com.camp.infra;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** infra 모듈의 빈 선언 지점. 영속성 설정은 이슈 #32 에서 여기에 붙는다. */
@Configuration
public class InfraConfiguration {

    /** 시각을 주입 가능한 형태로 둔다. 고정 시각으로 바꿔야 날짜 경계를 테스트할 수 있다. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
