package com.eventpulse.repository;

import com.eventpulse.domain.social.TopicAlias;
import com.eventpulse.domain.social.TopicAlias.TopicAliasId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TopicAliasRepository extends JpaRepository<TopicAlias, TopicAliasId> {

    List<TopicAlias> findByInterest_Id(UUID interestId);

    List<TopicAlias> findByInterest_IdAndId_Source(UUID interestId, String source);
}
