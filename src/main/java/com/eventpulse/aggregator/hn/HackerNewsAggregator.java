package com.eventpulse.aggregator.hn;

import com.eventpulse.domain.feed.FeedItem;
import com.eventpulse.domain.interest.Interest;
import com.eventpulse.repository.InterestRepository;
import com.eventpulse.repository.TopicAliasRepository;
import com.eventpulse.service.FeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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
 * Pulls HN top stories on a schedule, matches each title against the keyword
 * aliases registered for source="hn", and upserts a FeedItem tagged with the
 * matched interests. A title without any matching alias is dropped — we only
 * surface items that are relevant to our taxonomy.
 *
 * Classification is intentionally simple right now: case-insensitive substring
 * match with whole-word boundaries. Good enough for a v0; can be replaced with
 * an LLM classifier later without changing the upsert path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HackerNewsAggregator {

    private static final String SOURCE = "hn";
    private static final int FETCH_LIMIT = 100;

    private final HackerNewsClient client;
    private final TopicAliasRepository topicAliasRepository;
    private final InterestRepository interestRepository;
    private final FeedService feedService;

    /**
     * Every 30 minutes, with a 1-minute initial delay so the app finishes booting first.
     */
    @Scheduled(initialDelay = 60_000, fixedDelay = 30 * 60_000)
    public void poll() {
        ingest();
    }

    public int ingest() {
        Map<String, List<UUID>> aliasIndex = loadAliasIndex();
        if (aliasIndex.isEmpty()) {
            log.info("HN ingest skipped: no topic aliases registered for source={}", SOURCE);
            return 0;
        }

        List<Long> ids = client.topStoryIds();
        if (ids.isEmpty()) return 0;

        int upserts = 0;
        for (Long id : ids.subList(0, Math.min(FETCH_LIMIT, ids.size()))) {
            HackerNewsItem item = client.item(id);
            if (item == null || !item.isLiveStory()) continue;

            Set<UUID> matchedIds = classify(item.title(), aliasIndex);
            if (matchedIds.isEmpty()) continue;

            ingestOne(item, matchedIds);
            upserts++;
        }
        log.info("HN ingest complete: scanned {} stories, upserted {}", ids.size(), upserts);
        return upserts;
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

    private Set<UUID> classify(String title, Map<String, List<UUID>> aliasIndex) {
        String haystack = " " + title.toLowerCase(Locale.ROOT) + " ";
        Set<UUID> hits = new HashSet<>();
        for (Map.Entry<String, List<UUID>> e : aliasIndex.entrySet()) {
            if (containsWord(haystack, e.getKey())) hits.addAll(e.getValue());
        }
        return hits;
    }

    /**
     * Whole-word containment so "ai" doesn't match "rain" but does match "AI breakthrough".
     * Uses simple boundary characters rather than regex for speed and predictability.
     */
    private static boolean containsWord(String haystack, String needle) {
        int idx = haystack.indexOf(needle);
        while (idx >= 0) {
            char before = haystack.charAt(idx - 1);
            int after = idx + needle.length();
            char next = after < haystack.length() ? haystack.charAt(after) : ' ';
            if (!Character.isLetterOrDigit(before) && !Character.isLetterOrDigit(next)) return true;
            idx = haystack.indexOf(needle, idx + 1);
        }
        return false;
    }
}
