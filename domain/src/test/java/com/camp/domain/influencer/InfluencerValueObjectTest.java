package com.camp.domain.influencer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.camp.domain.shared.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** 등록 요청이 도메인에 닿는 지점의 값 검증. 여기서 막으면 저장 경로 전체가 같은 규칙을 따른다. */
class InfluencerValueObjectTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("이름이 비어 있으면 거부한다")
    void rejectsBlankName(String name) {
        assertThatThrownBy(() -> InfluencerName.of(name))
                .isInstanceOf(InvalidInfluencerNameException.class)
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("이름이 51 자면 거부하고 1 자와 50 자는 허용한다")
    void rejectsNameLongerThanFifty() {
        assertThatThrownBy(() -> InfluencerName.of("가".repeat(51)))
                .isInstanceOf(InvalidInfluencerNameException.class)
                .hasMessageContaining("51");

        assertThatCode(() -> InfluencerName.of("가")).doesNotThrowAnyException();
        assertThatCode(() -> InfluencerName.of("가".repeat(50))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, -1_000L})
    @DisplayName("팔로워 수가 음수면 거부한다")
    void rejectsNegativeFollowerCount(long value) {
        assertThatThrownBy(() -> FollowerCount.of(value))
                .isInstanceOf(InvalidFollowerCountException.class)
                .hasMessageContaining(String.valueOf(value));
    }

    @Test
    @DisplayName("팔로워 수 0 은 허용한다")
    void allowsZeroFollowerCount() {
        assertThat(FollowerCount.of(0).value()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("채널 식별자가 비어 있으면 거부한다")
    void rejectsBlankChannelIdentifier(String identifier) {
        assertThatThrownBy(() -> ChannelIdentifier.of(identifier))
                .isInstanceOf(InvalidChannelIdentifierException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("소속 형태가 AGENCY 인데 소속사명이 없으면 거부한다")
    void rejectsAgencyWithoutName(String agencyName) {
        assertThatThrownBy(() -> Affiliation.agency(agencyName)).isInstanceOf(AgencyNameRequiredException.class);
    }

    @Test
    @DisplayName("FREELANCER 는 소속사명을 갖지 않는다")
    void freelancerHasNoAgencyName() {
        assertThat(Affiliation.freelancer().agencyName()).isNull();
        assertThat(new Affiliation(AffiliationType.FREELANCER, "소속사").agencyName())
                .isNull();
    }
}
