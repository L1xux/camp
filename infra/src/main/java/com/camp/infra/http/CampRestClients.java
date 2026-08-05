package com.camp.infra.http;

import java.net.http.HttpClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 외부 호출용 RestClient 는 여기서만 만든다. ArchitectureTest 가 다른 곳의 직접 생성을 막는다. */
public final class CampRestClients {

    private CampRestClients() {}

    public static RestClient create(String baseUrl) {
        return create(baseUrl, CampHttpPolicy.defaults());
    }

    public static RestClient create(String baseUrl, CampHttpPolicy policy) {
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(policy.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(policy.readTimeout());
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(new RetryInterceptor(policy))
                .build();
    }
}
