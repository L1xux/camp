package com.camp.infra.http;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** 5xx 와 IO 실패를 지수 백오프로 재시도한다. 멱등 메서드만 대상이고 생성 계열은 0회다. */
final class RetryInterceptor implements ClientHttpRequestInterceptor {

    private static final Set<HttpMethod> IDEMPOTENT = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);

    private final CampHttpPolicy policy;

    RetryInterceptor(CampHttpPolicy policy) {
        this.policy = policy;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        int retries = IDEMPOTENT.contains(request.getMethod()) ? policy.maxRetries() : 0;

        for (int attempt = 0; ; attempt++) {
            try {
                ClientHttpResponse response = execution.execute(request, body);
                if (attempt < retries && response.getStatusCode().is5xxServerError()) {
                    response.close();
                    backOff(attempt);
                    continue;
                }
                return response;
            } catch (IOException e) {
                if (attempt >= retries) {
                    throw e;
                }
                backOff(attempt);
            }
        }
    }

    /** 대기 시간은 기본 지연의 2^시도 배다. 인터럽트는 삼키지 않고 IO 실패로 올린다. */
    private void backOff(int attempt) throws InterruptedIOException {
        long millis = policy.retryBaseDelay().toMillis() * (1L << attempt);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("재시도 대기 중 인터럽트");
        }
    }
}
