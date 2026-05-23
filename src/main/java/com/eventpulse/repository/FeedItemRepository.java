package com.eventpulse.repository;

import com.eventpulse.domain.feed.FeedItem;
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
        left join fetch fi.relatedEvent
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
     * Items the user has SAVE-engaged, newest-saved first.
     */
    @Query("""
        select fi from FeedItem fi
        left join fetch fi.relatedEvent
        join UserEngagement ue on ue.feedItem = fi
        where ue.user.id = :userId
          and ue.id.action = com.eventpulse.domain.engagement.EngagementAction.SAVE
        order by ue.createdAt desc
    """)
    List<FeedItem> findSavedByUser(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Items the user has HIDE-engaged, newest-hidden first.
     */
    @Query("""
        select fi from FeedItem fi
        left join fetch fi.relatedEvent
        join UserEngagement ue on ue.feedItem = fi
        where ue.user.id = :userId
          and ue.id.action = com.eventpulse.domain.engagement.EngagementAction.HIDE
        order by ue.createdAt desc
    """)
    List<FeedItem> findHiddenByUser(@Param("userId") UUID userId, Pageable pageable);
}
