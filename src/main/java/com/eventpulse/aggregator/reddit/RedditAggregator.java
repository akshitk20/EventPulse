package com.eventpulse.aggregator.reddit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Polls a fixed list of subreddits for hot posts, classifies each by subreddit
 * name against source='reddit' aliases, and delegates persistence to the ingest
 * service for transactional safety.
 *
 * Classification is structural: posts inherit their subreddit's interests via
 * the topic_aliases table. r/movies posts get the 'movie' interest, r/MarvelStudios
 * posts get 'movie-mcu', etc. Subscription expansion (UserInterestService.subscribe)
 * already cascades parent → leaf, so a Movies subscriber sees both r/movies and
 * r/MarvelStudios without needing duplicate tagging here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedditAggregator {

    /**
     * Curated list. Spans Movies, TV, Anime, Music (general/pop/hip-hop/Bollywood),
     * and Gaming. Add subreddits here when expanding the taxonomy and add a matching
     * row in V_*__seed_reddit_topic_aliases.sql.
     */
    private static final List<String> SUBREDDITS = List.of(
        "movies", "MarvelStudios", "MovieDetails",
        "television", "anime",
        "Music", "popheads", "hiphopheads", "BollyBlindsNGossip",
        "Games", "pcgaming", "gaming"
    );

    private static final int LIMIT = 25;

    /**
     * Score floor weeds out fresh-but-still-rising Hot posts. Reddit's Hot
     * ranking already favors quality, so this is a soft second filter rather
     * than a primary one.
     */
    private static final int SCORE_FLOOR = 50;

    private final RedditClient client;
    private final RedditIngestService ingestService;

    /**
     * Hourly poll. 120s initial delay so it doesn't collide with the HN
     * (60s) and SportsDB (90s) boot ticks.
     */
    @Scheduled(initialDelay = 120_000, fixedDelay = 60 * 60_000)
    public void poll() {
        ingest();
    }

    public int ingest() {
        Map<String, List<UUID>> aliasIndex = ingestService.loadAliasIndex();
        if (aliasIndex.isEmpty()) {
            log.info("Reddit ingest skipped: no topic aliases registered for source={}",
                RedditIngestService.SOURCE);
            return 0;
        }

        int upserts = 0;
        for (String subreddit : SUBREDDITS) {
            upserts += pollSubreddit(subreddit, aliasIndex);
        }
        log.info("Reddit ingest complete: upserted {}", upserts);
        return upserts;
    }

    private int pollSubreddit(String subreddit, Map<String, List<UUID>> aliasIndex) {
        List<RedditPost> posts = client.hotForSubreddit(subreddit, LIMIT);
        int upserts = 0;
        for (RedditPost post : posts) {
            if (!post.isPresentable(SCORE_FLOOR)) continue;
            Set<UUID> matched = classify(post, aliasIndex);
            if (matched.isEmpty()) continue;
            try {
                ingestService.ingestOne(post, matched);
                upserts++;
            } catch (Exception e) {
                log.warn("Reddit ingestOne failed for post {} in r/{}: {}",
                    post.id(), subreddit, e.getMessage());
            }
        }
        return upserts;
    }

    /**
     * Single key lookup: the lowercased subreddit name. Posts inherit only their
     * subreddit's interests; no per-title keyword matching at this layer.
     */
    private Set<UUID> classify(RedditPost post, Map<String, List<UUID>> aliasIndex) {
        if (post.subreddit() == null) return Collections.emptySet();
        List<UUID> ids = aliasIndex.get(post.subreddit().toLowerCase(Locale.ROOT));
        return ids == null ? Collections.emptySet() : Set.copyOf(ids);
    }
}
