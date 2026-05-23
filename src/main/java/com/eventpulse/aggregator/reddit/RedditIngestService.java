package com.eventpulse.aggregator.reddit;

import com.eventpulse.domain.feed.FeedItem;
import com.eventpulse.domain.interest.Interest;
import com.eventpulse.repository.InterestRepository;
import com.eventpulse.repository.TopicAliasRepository;
import com.eventpulse.service.FeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Transactional helpers for the Reddit aggregator. Lives in its own bean so
 * Spring's proxy applies the @Transactional boundaries when the orchestrator
 * (RedditAggregator) calls these methods. Same shape as HackerNewsIngestService —
 * see its javadoc for the rationale around self-invocation.
 */
@Service
@RequiredArgsConstructor
public class RedditIngestService {

    static final String SOURCE = "reddit";

    private final TopicAliasRepository topicAliasRepository;
    private final InterestRepository interestRepository;
    private final FeedService feedService;

    /**
     * Build a lowercase-query -> interest-IDs index for source='reddit'.
     * IDs only (no Interest entities) so the map can outlive this transaction.
     */
    @Transactional(readOnly = true)
    public Map<String, List<UUID>> loadAliasIndex() {
        Map<String, List<UUID>> index = new HashMap<>();
        topicAliasRepository.findAll().stream()
            .filter(a -> SOURCE.equals(a.getId().getSource()))
            .forEach(a -> index
                .computeIfAbsent(a.getId().getQuery().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                .add(a.getId().getInterestId()));
        return index;
    }

    /**
     * Per-post transaction: resolves matched interest IDs to managed entities and
     * either upserts a new FeedItem or refreshes an existing one. We refresh title,
     * url, thumbnail, metadata (score/comments change) but NOT publishedAt — Reddit
     * post creation time is fixed, and freezing it preserves ordering when the same
     * post bubbles up in Hot a day later.
     */
    @Transactional
    public void ingestOne(RedditPost post, Set<UUID> matchedIds) {
        Set<Interest> matched = new HashSet<>();
        for (UUID iid : matchedIds) matched.add(interestRepository.getReferenceById(iid));

        Map<String, Object> metadata = new HashMap<>();
        putIfPresent(metadata, "subreddit", post.subreddit());
        putIfPresent(metadata, "author", post.author());
        putIfPresent(metadata, "permalink", post.permalinkUrl());
        putIfPresent(metadata, "postHint", post.post_hint());
        if (post.score() != null) metadata.put("score", post.score());
        if (post.num_comments() != null) metadata.put("comments", post.num_comments());

        FeedItem candidate = FeedItem.builder()
            .source(SOURCE)
            .sourceId(post.id())
            .url(post.externalUrl())
            .title(post.title())
            .thumbnailUrl(post.thumbnailUrl())
            .publishedAt(post.publishedAt())
            .interests(new HashSet<>(matched))
            .metadata(new HashMap<>(metadata))
            .build();

        feedService.upsertBySource(candidate, existing -> {
            existing.setTitle(post.title());
            existing.setUrl(post.externalUrl());
            existing.setThumbnailUrl(post.thumbnailUrl());
            existing.setMetadata(new HashMap<>(metadata));
            existing.getInterests().addAll(matched);
        });
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) map.put(key, value);
    }
}
