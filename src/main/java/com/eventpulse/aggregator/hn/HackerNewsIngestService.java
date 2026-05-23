package com.eventpulse.aggregator.hn;

import com.eventpulse.domain.feed.FeedItem;
import com.eventpulse.domain.interest.Interest;
import com.eventpulse.repository.InterestRepository;
import com.eventpulse.repository.TopicAliasRepository;
import com.eventpulse.service.FeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Transactional helpers for the HN aggregator. Lives in its own bean so that
 * Spring's proxy applies the @Transactional boundaries when the orchestrator
 * (HackerNewsAggregator) calls these methods. Self-invocation within a single
 * bean would bypass the proxy and silently lose the transaction — keeping these
 * separate makes the boundaries reliable.
 */
@Service
@RequiredArgsConstructor
public class HackerNewsIngestService {

    static final String SOURCE = "hn";

    private final TopicAliasRepository topicAliasRepository;
    private final InterestRepository interestRepository;
    private final FeedService feedService;

    /**
     * Load aliases once per ingest, projecting to interest IDs only. Avoids holding
     * lazy Interest proxies across transaction boundaries — the proxies get resolved
     * later inside ingestOne()'s transaction via getReferenceById.
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
     * Per-item transaction: resolves matched interest IDs to managed entities and either
     * upserts a new FeedItem or updates an existing one. Kept narrow so a poison item
     * fails just one tx instead of aborting the whole poll.
     */
    @Transactional
    public void ingestOne(HackerNewsItem item, Set<UUID> matchedIds) {
        Set<Interest> matched = new HashSet<>();
        for (UUID iid : matchedIds) matched.add(interestRepository.getReferenceById(iid));

        FeedItem candidate = FeedItem.builder()
            .source(SOURCE)
            .sourceId(String.valueOf(item.id()))
            .url(item.permalinkUrl())
            .title(item.title())
            .publishedAt(item.time() == null ? null
                : OffsetDateTime.ofInstant(Instant.ofEpochSecond(item.time()), ZoneOffset.UTC))
            .interests(new HashSet<>(matched))
            .metadata(new HashMap<>(Map.of("by", item.by() == null ? "" : item.by())))
            .build();

        feedService.upsertBySource(candidate, existing -> {
            existing.setTitle(item.title());
            existing.setUrl(item.permalinkUrl());
            existing.getInterests().addAll(matched);
        });
    }
}
