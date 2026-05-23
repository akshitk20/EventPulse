package com.eventpulse.aggregator.sportsdb;

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
 * Transactional helpers for the TheSportsDB aggregator. Lives in its own bean
 * so Spring's proxy applies the @Transactional boundaries when the orchestrator
 * (SportsDbAggregator) calls these methods. Same shape as HackerNewsIngestService —
 * see its javadoc for the rationale around self-invocation.
 */
@Service
@RequiredArgsConstructor
public class SportsDbIngestService {

    static final String SOURCE = "sportsdb";

    private final TopicAliasRepository topicAliasRepository;
    private final InterestRepository interestRepository;
    private final FeedService feedService;

    /**
     * Build a lowercase-query -> interest-IDs index for source='sportsdb'.
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
     * Per-event transaction: resolves matched interest IDs to managed entities and
     * either upserts a new FeedItem or refreshes an existing one. On update we
     * refresh title, url, thumbnail, metadata, publishedAt (kickoff time can shift)
     * and re-add matched interests — important so a scheduled match's score and
     * status update once it finishes.
     */
    @Transactional
    public void ingestOne(SportsDbEvent ev, Set<UUID> matchedIds) {
        Set<Interest> matched = new HashSet<>();
        for (UUID iid : matchedIds) matched.add(interestRepository.getReferenceById(iid));

        Map<String, Object> metadata = new HashMap<>();
        putIfPresent(metadata, "league", ev.strLeague());
        putIfPresent(metadata, "sport", ev.strSport());
        putIfPresent(metadata, "status", ev.strStatus());
        putIfPresent(metadata, "homeTeam", ev.strHomeTeam());
        putIfPresent(metadata, "awayTeam", ev.strAwayTeam());
        putIfPresent(metadata, "homeScore", ev.intHomeScore());
        putIfPresent(metadata, "awayScore", ev.intAwayScore());

        FeedItem candidate = FeedItem.builder()
            .source(SOURCE)
            .sourceId(ev.idEvent())
            .url(ev.permalinkUrl())
            .title(ev.displayTitle())
            .thumbnailUrl(ev.thumbnailUrl())
            .publishedAt(ev.startsAt())
            .interests(new HashSet<>(matched))
            .metadata(new HashMap<>(metadata))
            .build();

        feedService.upsertBySource(candidate, existing -> {
            existing.setTitle(ev.displayTitle());
            existing.setUrl(ev.permalinkUrl());
            existing.setThumbnailUrl(ev.thumbnailUrl());
            existing.setPublishedAt(ev.startsAt());
            existing.setMetadata(new HashMap<>(metadata));
            existing.getInterests().addAll(matched);
        });
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) map.put(key, value);
    }
}
