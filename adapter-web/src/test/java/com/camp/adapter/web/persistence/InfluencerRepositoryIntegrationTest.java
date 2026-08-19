package com.camp.adapter.web.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.camp.adapter.web.CampApplication;
import com.camp.application.influencer.InfluencerRepository;
import com.camp.domain.influencer.Affiliation;
import com.camp.domain.influencer.Category;
import com.camp.domain.influencer.ChannelStatus;
import com.camp.domain.influencer.Influencer;
import com.camp.domain.influencer.InfluencerChannel;
import com.camp.domain.influencer.InfluencerId;
import com.camp.domain.influencer.InfluencerName;
import com.camp.domain.influencer.InfluencerStatus;
import com.camp.domain.influencer.Platform;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

/** 저장소가 애그리거트를 실제 Oracle 에 왕복시키는지 확인한다. 유니크 제약은 실제 DB 가 아니면 검증할 수 없다. */
@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = CampApplication.class)
class InfluencerRepositoryIntegrationTest {

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
    private InfluencerRepository repository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void clearTables() throws SQLException {
        // 유니크 제약이 플랫폼 전역이라 이전 테스트의 채널이 남으면 다음 테스트가 오염된다.
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM influencer_channel");
            statement.executeUpdate("DELETE FROM influencer");
        }
    }

    @Test
    @DisplayName("신규 인플루언서를 저장하면 식별자가 발급되고 조회로 복원된다")
    void savesAndReloadsProfile() {
        Influencer influencer = Influencer.register(
                InfluencerName.of("김뷰티"), Category.BEAUTY, Affiliation.agency("크리에이터랩"), "협업 이력 있음");

        InfluencerId id = repository.save(influencer);

        Influencer reloaded = repository.findById(id).orElseThrow();
        assertThat(reloaded.id()).isEqualTo(id);
        assertThat(reloaded.name().value()).isEqualTo("김뷰티");
        assertThat(reloaded.category()).isEqualTo(Category.BEAUTY);
        assertThat(reloaded.affiliation()).isEqualTo(Affiliation.agency("크리에이터랩"));
        assertThat(reloaded.memo()).isEqualTo("협업 이력 있음");
        assertThat(reloaded.status()).isEqualTo(InfluencerStatus.ACTIVE);
        assertThat(reloaded.channels()).isEmpty();
    }

    @Test
    @DisplayName("채널을 포함해 저장하면 채널 목록과 팔로워 합계 컬럼이 함께 복원된다")
    void savesAndReloadsChannels() throws SQLException {
        Influencer influencer =
                Influencer.register(InfluencerName.of("박테크"), Category.TECH, Affiliation.freelancer(), null);
        influencer.addChannel(InfluencerChannel.of(Platform.INSTAGRAM, "@parktech", 12_000));
        influencer.addChannel(InfluencerChannel.of(Platform.YOUTUBE, "UCparktech", 48_000));

        InfluencerId id = repository.save(influencer);

        Influencer reloaded = repository.findById(id).orElseThrow();
        assertThat(reloaded.channels()).hasSize(2);
        assertThat(reloaded.channels())
                .extracting(InfluencerChannel::platform)
                .containsExactly(Platform.INSTAGRAM, Platform.YOUTUBE);
        assertThat(reloaded.channels()).allMatch(channel -> channel.status() == ChannelStatus.ACTIVE);
        assertThat(reloaded.followerTotal()).isEqualTo(60_000);
        assertThat(followerTotalColumn(id)).isEqualTo(60_000);
    }

    @Test
    @DisplayName("다시 저장하면 프로필 변경과 추가된 채널이 반영된다")
    void updatesProfileAndAddsChannelOnResave() throws SQLException {
        Influencer influencer =
                Influencer.register(InfluencerName.of("이푸드"), Category.FOOD, Affiliation.freelancer(), null);
        influencer.addChannel(InfluencerChannel.of(Platform.INSTAGRAM, "@leefood", 5_000));
        InfluencerId id = repository.save(influencer);

        Influencer loaded = repository.findById(id).orElseThrow();
        loaded.changeProfile(InfluencerName.of("이푸드공식"), Category.LIFESTYLE, Affiliation.agency("맛집엔터"), "리브랜딩");
        loaded.addChannel(InfluencerChannel.of(Platform.YOUTUBE, "UCleefood", 30_000));
        repository.save(loaded);

        Influencer reloaded = repository.findById(id).orElseThrow();
        assertThat(reloaded.name().value()).isEqualTo("이푸드공식");
        assertThat(reloaded.category()).isEqualTo(Category.LIFESTYLE);
        assertThat(reloaded.affiliation()).isEqualTo(Affiliation.agency("맛집엔터"));
        assertThat(reloaded.channels()).hasSize(2);
        assertThat(reloaded.followerTotal()).isEqualTo(35_000);
        assertThat(followerTotalColumn(id)).isEqualTo(35_000);
    }

    @Test
    @DisplayName("없는 식별자로 조회하면 빈 결과다")
    void returnsEmptyForUnknownId() {
        assertThat(repository.findById(InfluencerId.of(999_999))).isEmpty();
    }

    @Test
    @DisplayName("다른 인플루언서가 같은 플랫폼의 같은 채널을 저장하면 유니크 제약이 막는다")
    void rejectsDuplicateChannelAcrossInfluencers() {
        Influencer first =
                Influencer.register(InfluencerName.of("최패션"), Category.FASHION, Affiliation.freelancer(), null);
        first.addChannel(InfluencerChannel.of(Platform.INSTAGRAM, "@choifashion", 1_000));
        repository.save(first);

        Influencer second =
                Influencer.register(InfluencerName.of("사칭계정"), Category.FASHION, Affiliation.freelancer(), null);
        second.addChannel(InfluencerChannel.of(Platform.INSTAGRAM, "@choifashion", 2_000));

        assertThatThrownBy(() -> repository.save(second)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("먼저 읽어둔 묵은 버전으로 저장하면 낙관적 잠금이 거부한다")
    void rejectsStaleVersionOnSave() {
        Influencer influencer =
                Influencer.register(InfluencerName.of("정라이프"), Category.LIFESTYLE, Affiliation.freelancer(), null);
        InfluencerId id = repository.save(influencer);

        Influencer first = repository.findById(id).orElseThrow();
        Influencer second = repository.findById(id).orElseThrow();
        first.changeProfile(InfluencerName.of("정라이프A"), Category.LIFESTYLE, Affiliation.freelancer(), null);
        repository.save(first);
        second.changeProfile(InfluencerName.of("정라이프B"), Category.LIFESTYLE, Affiliation.freelancer(), null);

        assertThatThrownBy(() -> repository.save(second)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(repository.findById(id).orElseThrow().name().value()).isEqualTo("정라이프A");
    }

    private long followerTotalColumn(InfluencerId id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT follower_total FROM influencer WHERE id = ?")) {
            statement.setLong(1, id.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }
}
