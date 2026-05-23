package com.eventpulse.repository;

import com.eventpulse.domain.engagement.EngagementAction;
import com.eventpulse.domain.engagement.UserEngagement;
import com.eventpulse.domain.engagement.UserEngagement.UserEngagementId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserEngagementRepository extends JpaRepository<UserEngagement, UserEngagementId> {

    List<UserEngagement> findByUser_Id(UUID userId);

    List<UserEngagement> findByUser_IdAndId_Action(UUID userId, EngagementAction action);

    long countByFeedItem_IdAndId_Action(UUID feedItemId, EngagementAction action);
}
