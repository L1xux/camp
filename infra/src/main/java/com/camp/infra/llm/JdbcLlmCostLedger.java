package com.camp.infra.llm;

import com.camp.application.llm.LlmCostLedger;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** llm_cost_daily 테이블에 비용을 쌓는다. 증가는 DB 의 행 잠금으로 직렬화된다. */
@Repository
public class JdbcLlmCostLedger implements LlmCostLedger {

    private static final String SELECT_TOTAL = "SELECT total_cost_usd FROM llm_cost_daily WHERE usage_date = ?";

    // 읽고 더해서 쓰는 것이 아니라 DB 안에서 더한다. 두 세션이 겹치면 뒤 세션이 갱신 후 값에 더한다.
    private static final String INCREMENT = """
            UPDATE llm_cost_daily
               SET total_cost_usd = total_cost_usd + ?,
                   updated_at = SYSTIMESTAMP
             WHERE usage_date = ?
            """;

    private static final String INSERT = "INSERT INTO llm_cost_daily (usage_date, total_cost_usd) VALUES (?, ?)";

    private final JdbcTemplate jdbc;

    public JdbcLlmCostLedger(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public BigDecimal totalSpentOn(LocalDate usageDate) {
        List<BigDecimal> found = jdbc.queryForList(SELECT_TOTAL, BigDecimal.class, Date.valueOf(usageDate));
        return found.isEmpty() ? BigDecimal.ZERO : found.get(0);
    }

    @Override
    public void add(LocalDate usageDate, BigDecimal costUsd) {
        Date date = Date.valueOf(usageDate);
        if (jdbc.update(INCREMENT, costUsd, date) > 0) {
            return;
        }
        try {
            jdbc.update(INSERT, date, costUsd);
        } catch (DuplicateKeyException e) {
            // 그 사이 다른 세션이 오늘 행을 만들었다. 갱신으로 돌아간다.
            jdbc.update(INCREMENT, costUsd, date);
        }
    }
}
