package com.camp.infra.influencer;

import com.camp.application.influencer.InfluencerRepository;
import com.camp.domain.influencer.Affiliation;
import com.camp.domain.influencer.AffiliationType;
import com.camp.domain.influencer.Category;
import com.camp.domain.influencer.ChannelIdentifier;
import com.camp.domain.influencer.ChannelStatus;
import com.camp.domain.influencer.FollowerCount;
import com.camp.domain.influencer.Influencer;
import com.camp.domain.influencer.InfluencerChannel;
import com.camp.domain.influencer.InfluencerId;
import com.camp.domain.influencer.InfluencerName;
import com.camp.domain.influencer.InfluencerStatus;
import com.camp.domain.influencer.Platform;
import java.sql.PreparedStatement;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** influencer 와 influencer_channel 두 테이블에 애그리거트를 저장한다. */
@Repository
public class JdbcInfluencerRepository implements InfluencerRepository {

    private static final String INSERT_INFLUENCER = """
            INSERT INTO influencer (name, category, affiliation_type, agency_name, memo, status, follower_total, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, 0)
            """;

    private static final String UPDATE_INFLUENCER = """
            UPDATE influencer
               SET name = ?, category = ?, affiliation_type = ?, agency_name = ?,
                   memo = ?, status = ?, follower_total = ?, version = version + 1, updated_at = SYSTIMESTAMP
             WHERE id = ? AND version = ?
            """;

    private static final String SELECT_INFLUENCER = """
            SELECT version, name, category, affiliation_type, agency_name, memo, status
              FROM influencer
             WHERE id = ?
            """;

    private static final String INSERT_CHANNEL = """
            INSERT INTO influencer_channel (influencer_id, platform, channel_identifier, follower_count, status)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_CHANNEL = """
            UPDATE influencer_channel
               SET follower_count = ?, status = ?
             WHERE influencer_id = ? AND platform = ? AND channel_identifier = ?
            """;

    private static final String SELECT_CHANNELS = """
            SELECT platform, channel_identifier, follower_count, status
              FROM influencer_channel
             WHERE influencer_id = ?
             ORDER BY id
            """;

    private final JdbcTemplate jdbc;

    public JdbcInfluencerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public InfluencerId save(Influencer influencer) {
        InfluencerId id = influencer.id() == null ? insert(influencer) : update(influencer);
        syncChannels(id, influencer.channels());
        return id;
    }

    @Override
    public Optional<Influencer> findById(InfluencerId id) {
        List<Influencer> found = jdbc.query(
                SELECT_INFLUENCER,
                (rs, rowNum) -> Influencer.reconstitute(
                        id,
                        rs.getLong("version"),
                        InfluencerName.of(rs.getString("name")),
                        Category.valueOf(rs.getString("category")),
                        toAffiliation(rs.getString("affiliation_type"), rs.getString("agency_name")),
                        rs.getString("memo"),
                        InfluencerStatus.valueOf(rs.getString("status")),
                        channelsOf(id)),
                id.value());
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    private InfluencerId insert(Influencer influencer) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(
                connection -> {
                    // Oracle 은 컬럼을 지정하지 않으면 생성 키 대신 ROWID 를 돌려준다.
                    PreparedStatement statement = connection.prepareStatement(INSERT_INFLUENCER, new String[] {"id"});
                    bindProfile(statement, influencer);
                    return statement;
                },
                keyHolder);
        return InfluencerId.of(Objects.requireNonNull(keyHolder.getKey()).longValue());
    }

    private InfluencerId update(Influencer influencer) {
        InfluencerId id = influencer.id();
        int updated = jdbc.update(
                UPDATE_INFLUENCER,
                influencer.name().value(),
                influencer.category().name(),
                influencer.affiliation().type().name(),
                influencer.affiliation().agencyName(),
                influencer.memo(),
                influencer.status().name(),
                influencer.followerTotal(),
                id.value(),
                influencer.version());
        if (updated == 0) {
            throw new OptimisticLockingFailureException(
                    "인플루언서가 없거나 다른 트랜잭션이 먼저 갱신했다. id: " + id + ", 읽은 version: " + influencer.version());
        }
        return id;
    }

    /** 채널은 물리 삭제가 없으므로 없는 것은 넣고 있는 것은 상태와 팔로워 수를 맞춘다. */
    private void syncChannels(InfluencerId id, List<InfluencerChannel> channels) {
        Set<String> existing = new HashSet<>(jdbc.query(
                SELECT_CHANNELS,
                (rs, rowNum) -> channelKey(rs.getString("platform"), rs.getString("channel_identifier")),
                id.value()));

        for (InfluencerChannel channel : channels) {
            String platform = channel.platform().name();
            String identifier = channel.identifier().value();
            if (existing.contains(channelKey(platform, identifier))) {
                jdbc.update(
                        UPDATE_CHANNEL,
                        channel.followerCount().value(),
                        channel.status().name(),
                        id.value(),
                        platform,
                        identifier);
            } else {
                jdbc.update(
                        INSERT_CHANNEL,
                        id.value(),
                        platform,
                        identifier,
                        channel.followerCount().value(),
                        channel.status().name());
            }
        }
    }

    private List<InfluencerChannel> channelsOf(InfluencerId id) {
        return jdbc.query(
                SELECT_CHANNELS,
                (rs, rowNum) -> new InfluencerChannel(
                        Platform.valueOf(rs.getString("platform")),
                        ChannelIdentifier.of(rs.getString("channel_identifier")),
                        FollowerCount.of(rs.getLong("follower_count")),
                        ChannelStatus.valueOf(rs.getString("status"))),
                id.value());
    }

    private void bindProfile(PreparedStatement statement, Influencer influencer) throws java.sql.SQLException {
        statement.setString(1, influencer.name().value());
        statement.setString(2, influencer.category().name());
        statement.setString(3, influencer.affiliation().type().name());
        statement.setString(4, influencer.affiliation().agencyName());
        statement.setString(5, influencer.memo());
        statement.setString(6, influencer.status().name());
        statement.setLong(7, influencer.followerTotal());
    }

    private static Affiliation toAffiliation(String type, String agencyName) {
        return AffiliationType.valueOf(type) == AffiliationType.AGENCY
                ? Affiliation.agency(agencyName)
                : Affiliation.freelancer();
    }

    private static String channelKey(String platform, String identifier) {
        return platform + "/" + identifier;
    }
}
