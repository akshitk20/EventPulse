package com.eventpulse.aggregator.youtube;

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
 * Transactional helpers for the YouTube aggregator. Mirrors {@link
 * com.eventpulse.aggregator.reddit.RedditIngestService} — the service exists in
 * its own bean so Spring's @Transactional proxy applies when the orchestrator
 * (YouTubeAggregator) calls in.
 *
 * Classification key is the channel handle (lowercase, no '@'). One handle can
 * map to multiple interests via the topic_aliases table — mirrors how the
 * subreddit list works for Reddit.
 */
@Service
@RequiredArgsConstructor
public class YouTubeIngestService {

    static final String SOURCE = "youtube";

    private final TopicAliasRepository topicAliasRepository;
    private final InterestRepository interestRepository;
    private final FeedService feedService;

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
     * Per-video transaction. Refreshes title/thumbnail/metadata on re-poll but not
     * publishedAt — videos' publish times are immutable and freezing the column
     * preserves chronological ordering when we re-encounter the same upload. Same
     * rationale as Reddit's ingestOne.
     */
    @Transactional
    public void ingestOne(YouTubeVideo video, Set<UUID> matchedIds) {
        Set<Interest> matched = new HashSet<>();
        for (UUID iid : matchedIds) matched.add(interestRepository.getReferenceById(iid));

        Map<String, Object> metadata = new HashMap<>();
        putIfPresent(metadata, "channelId", video.channelId());
        putIfPresent(metadata, "channelTitle", video.channelTitle());
        putIfPresent(metadata, "channelHandle", video.channelHandle());
        putIfPresent(metadata, "description", trim(video.description(), 500));

        FeedItem candidate = FeedItem.builder()
            .source(SOURCE)
            .sourceId(video.videoId())
            .url(video.watchUrl())
            .title(video.title())
            .thumbnailUrl(video.thumbnailUrl())
            .publishedAt(video.publishedAt())
            .interests(new HashSet<>(matched))
            .metadata(new HashMap<>(metadata))
            .build();

        feedService.upsertBySource(candidate, existing -> {
            existing.setTitle(video.title());
            existing.setUrl(video.watchUrl());
            existing.setThumbnailUrl(video.thumbnailUrl());
            existing.setMetadata(new HashMap<>(metadata));
            existing.getInterests().addAll(matched);
        });
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) map.put(key, value);
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
