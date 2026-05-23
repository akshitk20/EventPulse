package com.eventpulse.aggregator.hn;

import com.eventpulse.domain.feed.FeedItem;
import com.eventpulse.domain.interest.Interest;
import com.eventpulse.domain.social.TopicAlias;
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
    private final FeedService feedService;

    /**
     * Every 30 minutes, with a 1-minute initial delay so the app finishes booting first.
     */
    @Scheduled(initialDelay = 60_000, fixedDelay = 30 * 60_000)
    public void poll() {
        ingest();
    }

    @Transactional
    public int ingest() {
        Map<String, List<Interest>> aliasIndex = buildAliasIndex();
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

            Set<Interest> matched = classify(item.title(), aliasIndex);
            if (matched.isEmpty()) continue;

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
            upserts++;
        }
        log.info("HN ingest complete: scanned {} stories, upserted {}", ids.size(), upserts);
        return upserts;
    }

    /**
     * Build keyword -> interests map once per ingest. Multiple interests can share
     * a keyword (e.g. "world cup" -> cricket-wc and football-wc), so values are lists.
     */
    private Map<String, List<Interest>> buildAliasIndex() {
        List<TopicAlias> aliases = topicAliasRepository.findAll().stream()
            .filter(a -> SOURCE.equals(a.getId().getSource()))
            .toList();
        Map<String, List<Interest>> index = new HashMap<>();
        for (TopicAlias a : aliases) {
            index.computeIfAbsent(a.getId().getQuery().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                 .add(a.getInterest());
        }
        return index;
    }

    private Set<Interest> classify(String title, Map<String, List<Interest>> aliasIndex) {
        String haystack = " " + title.toLowerCase(Locale.ROOT) + " ";
        Set<Interest> hits = new HashSet<>();
        for (Map.Entry<String, List<Interest>> e : aliasIndex.entrySet()) {
            String needle = e.getKey();
            if (containsWord(haystack, needle)) {
                hits.addAll(e.getValue());
            }
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
