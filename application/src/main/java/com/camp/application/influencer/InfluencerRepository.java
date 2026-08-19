package com.camp.application.influencer;

import com.camp.domain.influencer.Influencer;
import com.camp.domain.influencer.InfluencerId;
import java.util.Optional;

/** 인플루언서 애그리거트 저장소. 구현체는 infra 에 있다. */
public interface InfluencerRepository {

    /** 식별자가 없으면 새로 넣고, 있으면 프로필과 채널을 애그리거트 상태로 맞춘다. */
    InfluencerId save(Influencer influencer);

    /** 채널까지 포함한 애그리거트 전체를 복원한다. */
    Optional<Influencer> findById(InfluencerId id);
}
