package com.camp.domain.influencer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 인플루언서 애그리거트. 채널 중복과 팔로워 합계가 여기서 결정된다. */
class InfluencerTest {

    private Influencer register() {
        return Influencer.register(InfluencerName.of("김서연"), Category.FASHION, Affiliation.freelancer(), null);
    }

    @Test
    @DisplayName("같은 플랫폼의 같은 식별자를 다시 추가하면 거부한다")
    void rejectsDuplicateChannel() {
        Influencer influencer = register();
        influencer.addChannel(InfluencerChannel.of(Platform.YOUTUBE, "UC_camp_demo", 120_000));

        assertThatThrownBy(() -> influencer.addChannel(InfluencerChannel.of(Platform.YOUTUBE, "UC_camp_demo", 999)))
                .isInstanceOf(DuplicateChannelException.class)
                .hasMessageContaining("UC_camp_demo");

        assertThat(influencer.channels()).hasSize(1);
    }

    @Test
    @DisplayName("채널을 추가할 때마다 팔로워 합계가 갱신된다")
    void recalculatesFollowerTotalOnChannelAdd() {
        Influencer influencer = register();
        assertThat(influencer.followerTotal()).isZero();

        influencer.addChannel(InfluencerChannel.of(Platform.YOUTUBE, "UC_camp_demo", 120_000));
        assertThat(influencer.followerTotal()).isEqualTo(120_000);

        influencer.addChannel(InfluencerChannel.of(Platform.INSTAGRAM, "camp_demo", 35_000));
        assertThat(influencer.followerTotal()).isEqualTo(155_000);
    }
}
