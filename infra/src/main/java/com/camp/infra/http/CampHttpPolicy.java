package com.camp.infra.http;

import java.time.Duration;

/** 외부 호출 공통 정책. 타임아웃 없는 호출과 2회 초과 재시도를 타입 수준에서 막는다. */
public record CampHttpPolicy(Duration connectTimeout, Duration readTimeout, int maxRetries, Duration retryBaseDelay) {

    private static final int MAX_ALLOWED_RETRIES = 2;

    public CampHttpPolicy {
        if (connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException("connectTimeout 은 양수여야 한다");
        }
        if (readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("readTimeout 은 양수여야 한다");
        }
        if (maxRetries < 0 || maxRetries > MAX_ALLOWED_RETRIES) {
            throw new IllegalArgumentException("maxRetries 는 0 이상 " + MAX_ALLOWED_RETRIES + " 이하여야 한다");
        }
        if (retryBaseDelay.isNegative()) {
            throw new IllegalArgumentException("retryBaseDelay 는 음수일 수 없다");
        }
    }

    public static CampHttpPolicy defaults() {
        return new CampHttpPolicy(Duration.ofSeconds(3), Duration.ofSeconds(10), 2, Duration.ofMillis(500));
    }
}
