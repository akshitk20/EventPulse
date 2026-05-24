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
     * Ordering blends two engagement-derived weights with recency:
     *
     *   1. <b>Source weight</b> — sum over the user's last 30 days of engagements
     *      on items sharing this item's source (SAVE=+2, CLICK=+0.5, HIDE=-3).
     *      A user who saves HN sees HN rise; one who hides YouTube sees it sink.
     *
     *   2. <b>Per-interest weight</b> — average, across <i>this item's</i> interests,
     *      of the per-interest engagement weight. An interest's weight is the same
     *      sum but bounded to engagements on items tagged with that interest. The
     *      LEFT JOIN means interests with no engagement contribute 0 (not NULL),
     *      so a music-only HIDE penalises music-tagged items but doesn't poison
     *      gaming-tagged items that share a source.
     *
     * Both subqueries apply a 30-day hard cutoff so old preferences don't
     * permanently shadow current behaviour. The {@code (user_id, created_at DESC)}
     * index makes the cutoff cheap.
     *
     * New users with no engagements get 0+0 from coalesce, collapsing to recency.
     *
     * Native query because the per-interest avg needs a derived-table subquery,
     * which JPQL doesn't support in scalar position. {@code feed_item_interests}
     * is unmapped on the entity but referenced directly by the inner subquery.
     * Hibernate maps the {@code fi.*} columns back to {@link FeedItem}; the
     * generated {@code search_vector} column is unmapped and silently ignored.
     * {@code relatedEvent} fetches lazily on access (no fetch-join in native);
     * the feed list view doesn't render relatedEvent fields, so this is fine.
     */
    @Query(value = """
        select fi.* from feed_items fi
        where exists (
            select 1 from feed_item_interests fii
            join user_interests ui on ui.interest_id = fii.interest_id
            where fii.feed_item_id = fi.id
              and ui.user_id = :userId
          )
          and not exists (
            select 1 from user_engagement ue
            where ue.user_id = :userId
              and ue.feed_item_id = fi.id
              and ue.action = 'hide'
          )
        order by (
            coalesce((
                select sum(case ue2.action
                    when 'save'  then 2.0
                    when 'click' then 0.5
                    when 'hide'  then -3.0
                    else 0.0
                end)
                from user_engagement ue2
                join feed_items fi2 on fi2.id = ue2.feed_item_id
                where ue2.user_id = :userId
                  and fi2.source = fi.source
                  and ue2.created_at > now() - interval '30 days'
            ), 0.0)
            +
            coalesce((
                select avg(per_interest_w) from (
                    select coalesce(sum(case ue3.action
                        when 'save'  then 2.0
                        when 'click' then 0.5
                        when 'hide'  then -3.0
                        else 0.0
                    end), 0.0) as per_interest_w
                    from feed_item_interests fii_cur
                    left join feed_item_interests fii_other
                        on fii_other.interest_id = fii_cur.interest_id
                    left join user_engagement ue3
                        on ue3.feed_item_id = fii_other.feed_item_id
                        and ue3.user_id = :userId
                        and ue3.created_at > now() - interval '30 days'
                    where fii_cur.feed_item_id = fi.id
                    group by fii_cur.interest_id
                ) per_interest
            ), 0.0)
        ) desc,
        fi.published_at desc nulls last,
        fi.fetched_at desc
        """,
        nativeQuery = true)
    List<FeedItem> findPersonalizedFeed(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Personalized feed filtered by source and/or interest, but no text query.
     * The {@code :source = ''} sentinel keeps the bind text-typed (a null String
     * binds as bytea on Postgres and breaks downstream comparisons).
     *
     * Same source + per-interest weighted ordering as {@link #findPersonalizedFeed}
     * with a 30-day cutoff. When the user filters to a single source, the source
     * weight collapses to a constant for all rows and per-interest avg + recency
     * take over — that's fine, narrowing the source is already what the user asked for.
     *
     * Cast on {@code :interestId} is needed because Postgres can't infer the type
     * of a null bind in a comparison; without the cast we'd hit "could not determine
     * data type of parameter".
     */
    @Query(value = """
        select fi.* from feed_items fi
        where exists (
            select 1 from feed_item_interests fii
            join user_interests ui on ui.interest_id = fii.interest_id
            where fii.feed_item_id = fi.id
              and ui.user_id = :userId
              and (cast(:interestId as uuid) is null or fii.interest_id = :interestId)
          )
          and (:source = '' or fi.source = :source)
          and not exists (
            select 1 from user_engagement ue
            where ue.user_id = :userId
              and ue.feed_item_id = fi.id
              and ue.action = 'hide'
          )
        order by (
            coalesce((
                select sum(case ue2.action
                    when 'save'  then 2.0
                    when 'click' then 0.5
                    when 'hide'  then -3.0
                    else 0.0
                end)
                from user_engagement ue2
                join feed_items fi2 on fi2.id = ue2.feed_item_id
                where ue2.user_id = :userId
                  and fi2.source = fi.source
                  and ue2.created_at > now() - interval '30 days'
            ), 0.0)
            +
            coalesce((
                select avg(per_interest_w) from (
                    select coalesce(sum(case ue3.action
                        when 'save'  then 2.0
                        when 'click' then 0.5
                        when 'hide'  then -3.0
                        else 0.0
                    end), 0.0) as per_interest_w
                    from feed_item_interests fii_cur
                    left join feed_item_interests fii_other
                        on fii_other.interest_id = fii_cur.interest_id
                    left join user_engagement ue3
                        on ue3.feed_item_id = fii_other.feed_item_id
                        and ue3.user_id = :userId
                        and ue3.created_at > now() - interval '30 days'
                    where fii_cur.feed_item_id = fi.id
                    group by fii_cur.interest_id
                ) per_interest
            ), 0.0)
        ) desc,
        fi.published_at desc nulls last,
        fi.fetched_at desc
        """,
        nativeQuery = true)
    List<FeedItem> filterPersonalizedFeed(
        @Param("userId") UUID userId,
        @Param("source") String source,
        @Param("interestId") UUID interestId,
        Pageable pageable
    );

    /**
     * FTS-backed personalized search: matches q against the {@code search_vector}
     * generated column (V6 migration) using {@code plainto_tsquery('english', :q)},
     * orders primarily by relevance ({@code ts_rank_cd}). Native query because
     * the {@code @@} operator and {@code ts_rank_cd} aren't in JPQL.
     *
     * Personalization signal applies as a secondary tiebreaker: when relevance is
     * close, the same source+per-interest weighted score (with 30-day cutoff) used
     * by the unfiltered feed nudges results toward sources/topics the user engages
     * with. We don't let it dominate ts_rank_cd — the user typed a query, relevance
     * leads.
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
                 (
                    coalesce((
                        select sum(case ue2.action
                            when 'save'  then 2.0
                            when 'click' then 0.5
                            when 'hide'  then -3.0
                            else 0.0
                        end)
                        from user_engagement ue2
                        join feed_items fi2 on fi2.id = ue2.feed_item_id
                        where ue2.user_id = :userId
                          and fi2.source = fi.source
                          and ue2.created_at > now() - interval '30 days'
                    ), 0.0)
                    +
                    coalesce((
                        select avg(per_interest_w) from (
                            select coalesce(sum(case ue3.action
                                when 'save'  then 2.0
                                when 'click' then 0.5
                                when 'hide'  then -3.0
                                else 0.0
                            end), 0.0) as per_interest_w
                            from feed_item_interests fii_cur
                            left join feed_item_interests fii_other
                                on fii_other.interest_id = fii_cur.interest_id
                            left join user_engagement ue3
                                on ue3.feed_item_id = fii_other.feed_item_id
                                and ue3.user_id = :userId
                                and ue3.created_at > now() - interval '30 days'
                            where fii_cur.feed_item_id = fi.id
                            group by fii_cur.interest_id
                        ) per_interest
                    ), 0.0)
                 ) desc,
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
