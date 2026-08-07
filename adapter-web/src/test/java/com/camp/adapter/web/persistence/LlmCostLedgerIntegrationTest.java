package com.camp.adapter.web.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.camp.adapter.web.CampApplication;
import com.camp.application.llm.LlmCostGuard;
import com.camp.application.llm.LlmCostLedger;
import com.camp.domain.llm.LlmDailyCostCapReachedException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

/** 비용 장부를 실제 Oracle 위에서 확인한다. 원자적 증가는 실제 DB 가 아니면 검증할 수 없다. */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = CampApplication.class)
@AutoConfigureMockMvc
class LlmCostLedgerIntegrationTest {

    // docker-compose 와 같은 이미지를 쓴다. 로컬과 CI 가 다른 DB 로 검증하면 의미가 없다.
    private static final DockerImageName IMAGE = DockerImageName.parse("gvenzl/oracle-free:23.26.2-slim-faststart");

    @Container
    static final OracleContainer ORACLE = new OracleContainer(IMAGE)
            .withUsername("camp")
            .withPassword("camp")
            .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ORACLE::getJdbcUrl);
        registry.add("spring.datasource.username", ORACLE::getUsername);
        registry.add("spring.datasource.password", ORACLE::getPassword);
    }

    @Autowired
    private LlmCostLedger ledger;

    @Autowired
    private LlmCostGuard guard;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("비용 카운터 병렬 증가 100회 → 정확히 100 반영")
    void parallelIncrementsAreNotLost() throws Exception {
        LocalDate date = LocalDate.of(2026, 1, 2);
        clear(date);

        int threads = 100;
        CountDownLatch startLine = new CountDownLatch(1);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                // 모두 같은 순간에 같은 행을 치도록 출발선을 맞춘다.
                startLine.await();
                ledger.add(date, BigDecimal.ONE);
                return null;
            });
        }

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<Void>> futures = tasks.stream().map(executor::submit).toList();
            startLine.countDown();
            for (Future<Void> future : futures) {
                future.get();
            }
        }

        assertThat(ledger.totalSpentOn(date)).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    @DisplayName("비용 상한 도달 상태에서 AI 질의는 차단되고 운영 API 는 정상이다")
    void blocksAiQueryButKeepsOperationalApiHealthy() throws Exception {
        LocalDate today = LocalDate.now(LlmCostGuard.RESET_ZONE);
        clear(today);
        ledger.add(today, new BigDecimal("2"));

        assertThatThrownBy(guard::requireQueryAllowed).isInstanceOf(LlmDailyCostCapReachedException.class);

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private void clear(LocalDate date) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("DELETE FROM llm_cost_daily WHERE usage_date = ?")) {
            statement.setDate(1, java.sql.Date.valueOf(date));
            statement.executeUpdate();
        }
    }
}
