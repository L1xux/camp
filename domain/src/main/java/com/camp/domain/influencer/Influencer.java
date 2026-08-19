package com.camp.domain.influencer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 인플루언서 애그리거트. 채널을 함께 소유하고 팔로워 합계를 채널에서 계산한다. */
public class Influencer {

    private final InfluencerId id;
    private final long version;
    private InfluencerName name;
    private Category category;
    private Affiliation affiliation;
    private String memo;
    private InfluencerStatus status;
    private final List<InfluencerChannel> channels;

    private Influencer(
            InfluencerId id,
            long version,
            InfluencerName name,
            Category category,
            Affiliation affiliation,
            String memo,
            InfluencerStatus status,
            List<InfluencerChannel> channels) {
        this.id = id;
        this.version = version;
        this.name = Objects.requireNonNull(name, "이름은 null 일 수 없다");
        this.category = Objects.requireNonNull(category, "카테고리는 null 일 수 없다");
        this.affiliation = Objects.requireNonNull(affiliation, "소속 정보는 null 일 수 없다");
        this.memo = normalizeMemo(memo);
        this.status = Objects.requireNonNull(status, "상태는 null 일 수 없다");
        this.channels = new ArrayList<>(channels);
    }

    /** 신규 등록. 저장 전이라 식별자가 없다. */
    public static Influencer register(InfluencerName name, Category category, Affiliation affiliation, String memo) {
        return new Influencer(null, 0, name, category, affiliation, memo, InfluencerStatus.ACTIVE, List.of());
    }

    /** 저장소에서 읽어온 상태를 그대로 복원한다. 등록 시점의 기본값을 적용하지 않는다. */
    public static Influencer reconstitute(
            InfluencerId id,
            long version,
            InfluencerName name,
            Category category,
            Affiliation affiliation,
            String memo,
            InfluencerStatus status,
            List<InfluencerChannel> channels) {
        Objects.requireNonNull(id, "복원에는 식별자가 필요하다");
        return new Influencer(id, version, name, category, affiliation, memo, status, channels);
    }

    /** 애그리거트 안의 중복만 막는다. 다른 인플루언서와의 중복은 저장소가 막는다. */
    public void addChannel(InfluencerChannel channel) {
        Objects.requireNonNull(channel, "채널은 null 일 수 없다");
        for (InfluencerChannel existing : channels) {
            if (existing.isSameChannelAs(channel)) {
                throw new DuplicateChannelException(channel.platform(), channel.identifier());
            }
        }
        channels.add(channel);
    }

    public void changeProfile(InfluencerName name, Category category, Affiliation affiliation, String memo) {
        this.name = Objects.requireNonNull(name, "이름은 null 일 수 없다");
        this.category = Objects.requireNonNull(category, "카테고리는 null 일 수 없다");
        this.affiliation = Objects.requireNonNull(affiliation, "소속 정보는 null 일 수 없다");
        this.memo = normalizeMemo(memo);
    }

    /** 검색이 쓰는 비정규화 컬럼의 원천. 채널이 바뀌면 이 값도 함께 바뀐다. */
    public long followerTotal() {
        long total = 0;
        for (InfluencerChannel channel : channels) {
            total += channel.followerCount().value();
        }
        return total;
    }

    public boolean hasChannelOn(Platform platform) {
        return channels.stream().anyMatch(channel -> channel.platform() == platform);
    }

    private static String normalizeMemo(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }
        return memo.strip();
    }

    /** 신규 등록 상태에서는 null 이다. */
    public InfluencerId id() {
        return id;
    }

    /** 낙관적 잠금용. 저장소가 읽은 시점의 값이고 갱신 성공 시 저장소가 올린다. */
    public long version() {
        return version;
    }

    public InfluencerName name() {
        return name;
    }

    public Category category() {
        return category;
    }

    public Affiliation affiliation() {
        return affiliation;
    }

    public String memo() {
        return memo;
    }

    public InfluencerStatus status() {
        return status;
    }

    public List<InfluencerChannel> channels() {
        return Collections.unmodifiableList(channels);
    }
}
