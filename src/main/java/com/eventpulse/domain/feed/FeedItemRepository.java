package com.eventpulse.domain.feed;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeedItemRepository extends JpaRepository<FeedItem, UUID> {

    Optional<FeedItem> findBySourceAndSourceId(String source, String sourceId);

    /**
     * Personalized feed: items tagged with any of the user's interests, newest first.
     * Hides items the user has explicitly hidden.
     */
    @Query("""
        select distinct fi from FeedItem fi
        join fi.interests i
        join UserInterest ui on ui.interest = i
        where ui.user.id = :userId
          and not exists (
            select 1 from UserEngagement ue
            where ue.user.id = :userId
              and ue.feedItem.id = fi.id
              and ue.id.action = com.eventpulse.domain.engagement.EngagementAction.HIDE
          )
        order by fi.publishedAt desc nulls last, fi.fetchedAt desc
    """)
    List<FeedItem> findPersonalizedFeed(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Items related to a specific event (for the event detail page).
     */
    List<FeedItem> findByRelatedEvent_IdOrderByPublishedAtDesc(UUID eventId, Pageable pageable);
}
