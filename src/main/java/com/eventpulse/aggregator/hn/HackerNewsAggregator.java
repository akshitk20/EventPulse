package com.eventpulse.aggregator.hn;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pulls HN top stories on a schedule, matches each title against the keyword
 * aliases registered for source="hn", and delegates each match to the ingest
 * service for transactional persistence. Titles without any matching alias are
 * dropped — we only surface items relevant to our taxonomy.
 *
 * Classification is intentionally simple right now: case-insensitive substring
 * match with whole-word boundaries. Good enough for a v0; can be replaced with
 * an LLM classifier later without changing the upsert path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HackerNewsAggregator {

    private static final int FETCH_LIMIT = 100;

    private final HackerNewsClient client;
    private final HackerNewsIngestService ingestService;

    /**
     * Every 30 minutes, with a 1-minute initial delay so the app finishes booting first.
     */
    @Scheduled(initialDelay = 60_000, fixedDelay = 30 * 60_000)
    public void poll() {
        ingest();
    }

    public int ingest() {
        Map<String, List<UUID>> aliasIndex = ingestService.loadAliasIndex();
        if (aliasIndex.isEmpty()) {
            log.info("HN ingest skipped: no topic aliases registered for source={}", HackerNewsIngestService.SOURCE);
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

            try {
                ingestService.ingestOne(item, matchedIds);
                upserts++;
            } catch (Exception e) {
                log.warn("HN ingestOne failed for story {}: {}", item.id(), e.getMessage());
            }
        }
        log.info("HN ingest complete: scanned {} stories, upserted {}", ids.size(), upserts);
        return upserts;
    }

    private Set<UUID> classify(String title, Map<String, List<UUID>> aliasIndex) {
        String haystack = " " + title.toLowerCase(Locale.ROOT) + " ";
        Set<UUID> hits = new HashSet<>();
        for (Map.Entry<String, List<UUID>> e : aliasIndex.entrySet()) {
            if (containsWord(haystack, e.getKey())) hits.addAll(new ArrayList<>(e.getValue()));
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
