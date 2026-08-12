package com.camp.adapter.web.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.camp.adapter.web.CampApplication;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

/** 앱을 실제로 띄워 Liquibase 가 컨테이너에 마이그레이션을 적용하는지 확인한다. */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = CampApplication.class)
class OracleMigrationIntegrationTest {

    // docker-compose 와 같은 이미지를 쓴다. 로컬과 CI 가 다른 DB 로 검증하면 의미가 없다.
    private static final DockerImageName IMAGE = DockerImageName.parse("gvenzl/oracle-free:23.26.2-slim-faststart");

    @Container
    static final OracleContainer ORACLE = new OracleContainer(IMAGE)
            .withUsername("camp")
            .withPassword("camp")
            // 이미지가 기동에 30초 안팎 걸린다. 느린 러너를 감안해 넉넉히 둔다.
            .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ORACLE::getJdbcUrl);
        registry.add("spring.datasource.username", ORACLE::getUsername);
        registry.add("spring.datasource.password", ORACLE::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Liquibase 가 V001 을 적용해 brand 테이블과 유니크 제약이 생긴다")
    void appliesBaselineMigration() throws SQLException {
        assertThat(count("SELECT COUNT(*) FROM user_tables WHERE table_name = 'BRAND'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM user_constraints WHERE constraint_name = 'UK_BRAND_CODE'"))
                .isEqualTo(1);
        // 전체 개수로 세면 마이그레이션이 추가될 때마다 이 테스트가 깨진다. V001 의 changeset 2 개만 확인한다.
        assertThat(count("SELECT COUNT(*) FROM databasechangelog WHERE filename LIKE '%V001%'"))
                .isEqualTo(2);
    }

    private int count(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
