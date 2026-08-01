package com.camp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FailingDemoTest {

    @Test
    void 일부러_실패한다() {
        assertThat(1).isEqualTo(2);
    }
}
