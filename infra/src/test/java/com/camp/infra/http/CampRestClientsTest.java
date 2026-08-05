package com.camp.infra.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** 모의 서버로 타임아웃과 재시도 정책을 검증한다. JDK 내장 HttpServer 라 추가 의존성이 없다. */
class CampRestClientsTest {

    private HttpServer server;
    private ExecutorService serverExecutor;
    private String baseUrl;

    private final List<Long> hitNanos = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/slow", exchange -> {
            hitNanos.add(System.nanoTime());
            sleepQuietly(1500);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/server-error", respondWith(500));
        server.createContext("/bad-request", respondWith(400));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    @Test
    @DisplayName("read 타임아웃 시나리오에서 설정 시간 내에 실패한다")
    void failsWithinConfiguredReadTimeout() {
        RestClient client = CampRestClients.create(
                baseUrl, new CampHttpPolicy(Duration.ofSeconds(1), Duration.ofMillis(300), 0, Duration.ZERO));

        long started = System.nanoTime();
        assertThatThrownBy(() -> client.get().uri("/slow").retrieve().toBodilessEntity())
                .isInstanceOf(ResourceAccessException.class);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertThat(elapsedMillis).isLessThan(1500);
    }

    @Test
    @DisplayName("5xx 응답은 백오프 간격으로 2회 재시도한 뒤 실패한다")
    void retriesTwiceWithBackoffOn5xx() {
        RestClient client = CampRestClients.create(
                baseUrl, new CampHttpPolicy(Duration.ofSeconds(1), Duration.ofSeconds(1), 2, Duration.ofMillis(100)));

        assertThatThrownBy(() -> client.get().uri("/server-error").retrieve().toBodilessEntity())
                .isInstanceOf(HttpServerErrorException.class);

        assertThat(hitNanos).hasSize(3);
        long firstGapMillis = (hitNanos.get(1) - hitNanos.get(0)) / 1_000_000;
        long secondGapMillis = (hitNanos.get(2) - hitNanos.get(1)) / 1_000_000;
        assertThat(firstGapMillis).isGreaterThanOrEqualTo(80);
        assertThat(secondGapMillis).isGreaterThanOrEqualTo(160);
        assertThat(secondGapMillis).isGreaterThan(firstGapMillis);
    }

    @Test
    @DisplayName("4xx 응답은 재시도 없이 즉시 실패한다")
    void failsImmediatelyOn4xx() {
        RestClient client = CampRestClients.create(
                baseUrl, new CampHttpPolicy(Duration.ofSeconds(1), Duration.ofSeconds(1), 2, Duration.ofMillis(100)));

        assertThatThrownBy(() -> client.get().uri("/bad-request").retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.class);

        assertThat(hitNanos).hasSize(1);
    }

    @Test
    @DisplayName("비멱등 요청은 실패해도 재시도 0회다")
    void neverRetriesNonIdempotentRequests() {
        RestClient client = CampRestClients.create(
                baseUrl, new CampHttpPolicy(Duration.ofSeconds(1), Duration.ofSeconds(1), 2, Duration.ofMillis(100)));

        assertThatThrownBy(() -> client.post().uri("/server-error").retrieve().toBodilessEntity())
                .isInstanceOf(HttpServerErrorException.class);

        assertThat(hitNanos).hasSize(1);
    }

    private HttpHandler respondWith(int status) {
        return exchange -> {
            hitNanos.add(System.nanoTime());
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        };
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
