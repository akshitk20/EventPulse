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
     * Personalized feed: items tagged with any of the user's interests.
     * Hides items the user has explicitly hidden.
     *
     * Ordering blends a per-source feedback weight derived from the user's
     * own engagement history (SAVE = +2, CLICK = +0.5, HIDE = -3) with
     * recency. A user who saves lots of HN sees HN rise; a user who hides
     * lots of YouTube sees it sink. New users with no engagements get the
     * weight as 0 from {@code coalesce}, which collapses to chronological.
     *
     * The weight is computed via a correlated scalar subquery in ORDER BY.
     * {@code user_engagement} is bounded by user activity (not feed size),
     * and the {@code (user_id, ...)} index makes this cheap per row.
     */
    @Query("""
        select fi from FeedItem fi
        left join fetch fi.relatedEvent
        where exists (
            select 1 from UserInterest ui
            join ui.interest i
            where ui.user.id = :userId
              and i member of fi.interests
          )
          and not exists (
            select 1 from UserEngagement ue
            where ue.user.id = :userId
              and ue.feedItem.id = fi.id
              and ue.id.action = com.eventpulse.domain.engagement.EngagementAction.HIDE
          )
        order by coalesce((
            select sum(case ue2.id.action
                when com.eventpulse.domain.engagement.EngagementAction.SAVE then 2.0
                when com.eventpulse.domain.engagement.EngagementAction.CLICK then 0.5
                when com.eventpulse.domain.engagement.EngagementAction.HIDE then -3.0
                else 0.0
            end)
            from UserEngagement ue2
            where ue2.user.id = :userId
              and ue2.feedItem.source = fi.source
        ), 0.0) desc,
        fi.publishedAt desc nulls last,
        fi.fetchedAt desc
    """)
    List<FeedItem> findPersonalizedFeed(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Personalized feed filtered by source and/or interest, but no text query.
     * The {@code :source = ''} sentinel keeps the bind text-typed (a null String
     * binds as bytea on Postgres and breaks downstream comparisons).
     *
     * Same source-level weight ordering as {@link #findPersonalizedFeed}. When the
     * user filters to a single source, the weight collapses to a constant for all
     * rows and recency takes over — that's fine, it's already what the user asked
     * for by narrowing the source.
     */
    @Query("""
        select fi from FeedItem fi
        left join fetch fi.relatedEvent
        where exists (
            select 1 from UserInterest ui
            join ui.interest i
            where ui.user.id = :userId
              and i member of fi.interests
              and (:interestId is null or i.id = :interestId)
          )
          and (:source = '' or fi.source = :source)
          and not exists (
            select 1 from UserEngagement ue
            where ue.user.id = :userId
              and ue.feedItem.id = fi.id
              and ue.id.action = com.eventpulse.domain.engagement.EngagementAction.HIDE
          )
        order by coalesce((
            select sum(case ue2.id.action
                when com.eventpulse.domain.engagement.EngagementAction.SAVE then 2.0
                when com.eventpulse.domain.engagement.EngagementAction.CLICK then 0.5
                when com.eventpulse.domain.engagement.EngagementAction.HIDE then -3.0
                else 0.0
            end)
            from UserEngagement ue2
            where ue2.user.id = :userId
              and ue2.feedItem.source = fi.source
        ), 0.0) desc,
        fi.publishedAt desc nulls last,
        fi.fetchedAt desc
    """)
    List<FeedItem> filterPersonalizedFeed(
        @Param("userId") UUID userId,
        @Param("source") String source,
        @Param("interestId") UUID interestId,
        Pageable pageable
    );

    /**
     * FTS-backed personalized search: matches q against the {@code search_vector}
     * generated column (V6 migration) using {@code plainto_tsquery('english', :q)},
     * orders by relevance ({@code ts_rank_cd}) then recency. Native query because
     * the {@code @@} operator and {@code ts_rank_cd} aren't in JPQL.
     *
     * Hibernate maps result columns by name back to the {@link FeedItem} entity.
     * The {@code search_vector} column is unmapped on the entity and silently
     * ignored. {@code relatedEvent} is fetched lazily on access (no fetch-join here);
     * the feed list view doesn't render relatedEvent fields, so this is fine.
     */
    @Query(value = """
        select fi.* from feed_items fi
        where fi.search_vector @@ plainto_tsquery('english', :q)
          and (:source = '' or fi.source = :source)
          and exists (
            select 1 from feed_item_interests fii
            join user_interests ui on ui.interest_id = fii.interest_id
            where fii.feed_item_id = fi.id
              and ui.user_id = :userId
              and (cast(:interestId as uuid) is null or fii.interest_id = :interestId)
          )
          and not exists (
            select 1 from user_engagement ue
            where ue.user_id = :userId
              and ue.feed_item_id = fi.id
              and ue.action = 'hide'
          )
        order by ts_rank_cd(fi.search_vector, plainto_tsquery('english', :q)) desc,
                 fi.published_at desc nulls last,
                 fi.fetched_at desc
        """,
        nativeQuery = true)
    List<FeedItem> ftsSearchPersonalizedFeed(
        @Param("userId") UUID userId,
        @Param("q") String q,
        @Param("source") String source,
        @Param("interestId") UUID interestId,
        Pageable pageable
    );

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
