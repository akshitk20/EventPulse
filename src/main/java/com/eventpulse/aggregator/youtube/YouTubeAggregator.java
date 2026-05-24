package com.eventpulse.aggregator.youtube;

import com.eventpulse.aggregator.youtube.YouTubeClient.ChannelRef;
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
 * Polls a curated set of YouTube channels for recent uploads, classifies each
 * by channel handle, and delegates persistence to {@link YouTubeIngestService}.
 *
 * Channels are listed by handle (the human-readable @name) rather than channel
 * ID because handles are stable, readable, and trivially editable. The client
 * resolves handle -> channelId -> uploads playlist once and caches the result
 * for the JVM lifetime.
 *
 * Classification: a single key lookup against the alias index, same shape as
 * the Reddit aggregator. Subscription expansion (parent -> leaf) happens in
 * UserInterestService.subscribe, so handles can be tagged with their narrowest
 * applicable interest and parents inherit transparently.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeAggregator {

    /**
     * Curated channel handles. Add a row here AND a matching topic_aliases entry
     * (V_*__seed_youtube_topic_aliases.sql) when extending. Lowercase, no '@'.
     *
     * Channels were picked for: (1) topical fit with the existing taxonomy,
     * (2) regular upload cadence, (3) strong English titles for FTS.
     */
    private static final List<String> HANDLES = List.of(
        // Tech / dev (no current interest — placeholder for when we add one)
        // "mkbhd",

        // Movies
        "screenrant", "rottentomatoes",

        // Music
        "vevo",

        // Gaming
        "ign", "gamespot",

        // Sports
        "nba", "espn"
    );

    /**
     * Per-channel fetch cap. YouTube returns up to 50 per page; 10 is plenty for
     * a per-channel poll given that the daily quota matters more than catching
     * every upload — a missed video shows up the next poll.
     */
    private static final int PER_CHANNEL_LIMIT = 10;

    private final YouTubeClient client;
    private final YouTubeIngestService ingestService;

    /**
     * Hourly poll. 180s initial delay to land after HN (60s), Reddit (120s), and
     * SportsDB (90s) so boot ticks don't all fire at once.
     */
    @Scheduled(initialDelay = 180_000, fixedDelay = 60 * 60_000)
    public void poll() {
        ingest();
    }

    public int ingest() {
        if (!client.isConfigured()) {
            log.info("YouTube ingest skipped: no API key configured");
            return 0;
        }
        Map<String, List<UUID>> aliasIndex = ingestService.loadAliasIndex();
        if (aliasIndex.isEmpty()) {
            log.info("YouTube ingest skipped: no topic aliases registered for source={}",
                YouTubeIngestService.SOURCE);
            return 0;
        }

        int upserts = 0;
        for (String handle : HANDLES) {
            upserts += pollChannel(handle, aliasIndex);
        }
        log.info("YouTube ingest complete: upserted {}", upserts);
        return upserts;
    }

    private int pollChannel(String handle, Map<String, List<UUID>> aliasIndex) {
        Set<UUID> matched = classify(handle, aliasIndex);
        // Skip the channels.list call entirely if no interest is mapped — saves
        // one quota unit per orphan handle. This makes adding a channel
        // before its alias is seeded a no-op rather than a wasted quota burn.
        if (matched.isEmpty()) {
            log.debug("YouTube channel @{} has no interest mapping, skipping", handle);
            return 0;
        }
        ChannelRef channel = client.resolveChannel(handle);
        if (channel == null) return 0;

        List<YouTubeVideo> videos = client.recentUploads(channel, PER_CHANNEL_LIMIT);
        int upserts = 0;
        for (YouTubeVideo video : videos) {
            if (!video.isPresentable()) continue;
            try {
                ingestService.ingestOne(video, matched);
                upserts++;
            } catch (Exception e) {
                log.warn("YouTube ingestOne failed for video {} in @{}: {}",
                    video.videoId(), handle, e.getMessage());
            }
        }
        return upserts;
    }

    private Set<UUID> classify(String handle, Map<String, List<UUID>> aliasIndex) {
        if (handle == null) return Collections.emptySet();
        List<UUID> ids = aliasIndex.get(handle.toLowerCase(Locale.ROOT));
        return ids == null ? Collections.emptySet() : Set.copyOf(ids);
    }
}
