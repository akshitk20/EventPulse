package com.eventpulse.aggregator.youtube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Thin wrapper over the YouTube Data API v3. We use the cheap endpoints only:
 *   - channels.list(forHandle=, part=contentDetails,snippet)  — 1 quota unit per call
 *   - playlistItems.list(playlistId=<uploads>)                — 1 quota unit per call
 *
 * Notably we DO NOT use search.list (100 units per call), which would blow the
 * default 10k/day quota fast. The uploads playlist is exposed on every channel
 * as contentDetails.relatedPlaylists.uploads — listing it gives the channel's
 * recent videos in publish order, no search needed.
 *
 * Channel handles are resolved once and cached for the JVM lifetime: handles
 * don't change in practice, and a missed cache miss after a restart costs one
 * extra quota unit per channel. We don't try to rebuild the cache on failure
 * (that would amplify quota spend during a YT outage).
 */
@Slf4j
@Component
public class YouTubeClient {

    private final RestClient restClient;
    private final String apiKey;

    /**
     * handle (lowercase, no '@') -> [channelId, uploadsPlaylistId, channelTitle].
     * Mutated by resolveChannel(); reads via Map.copyOf are safe under concurrent
     * polls because the scheduler is single-threaded and we never rewrite an entry.
     */
    private final Map<String, ChannelRef> channelCache = new HashMap<>();

    public YouTubeClient(
        @Value("${eventpulse.youtube.base-url:https://www.googleapis.com/youtube/v3}") String baseUrl,
        @Value("${eventpulse.youtube.api-key:}") String apiKey
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Resolve a YouTube handle (without leading '@', lowercase) to its channelId
     * and uploads playlist. Returns null on failure — callers skip the channel.
     */
    public ChannelRef resolveChannel(String handle) {
        if (handle == null || handle.isBlank()) return null;
        String key = handle.toLowerCase(Locale.ROOT);
        ChannelRef cached = channelCache.get(key);
        if (cached != null) return cached;

        try {
            ChannelListResponse resp = restClient.get()
                .uri(uri -> uri
                    .path("/channels")
                    .queryParam("part", "contentDetails,snippet")
                    .queryParam("forHandle", "@" + key)
                    .queryParam("key", apiKey)
                    .build())
                .retrieve()
                .body(ChannelListResponse.class);
            if (resp == null || resp.items() == null || resp.items().isEmpty()) {
                log.warn("YouTube channels.list returned no match for handle @{}", key);
                return null;
            }
            ChannelItem item = resp.items().get(0);
            String uploads = item.contentDetails() == null ? null
                : item.contentDetails().relatedPlaylists() == null ? null
                : item.contentDetails().relatedPlaylists().uploads();
            if (uploads == null || uploads.isBlank()) return null;
            String title = item.snippet() == null ? key : item.snippet().title();
            ChannelRef ref = new ChannelRef(item.id(), uploads, title, key);
            channelCache.put(key, ref);
            return ref;
        } catch (Exception e) {
            log.warn("YouTube channels.list failed for handle @{}: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * Fetch the most recent {@code limit} uploads from the channel's uploads playlist,
     * normalized to {@link YouTubeVideo}. Order is publish-DESC (YouTube API guarantee).
     */
    public List<YouTubeVideo> recentUploads(ChannelRef channel, int limit) {
        if (channel == null) return Collections.emptyList();
        try {
            PlaylistItemsResponse resp = restClient.get()
                .uri(uri -> uri
                    .path("/playlistItems")
                    .queryParam("part", "snippet,contentDetails")
                    .queryParam("playlistId", channel.uploadsPlaylistId())
                    .queryParam("maxResults", Math.min(Math.max(limit, 1), 50))
                    .queryParam("key", apiKey)
                    .build())
                .retrieve()
                .body(PlaylistItemsResponse.class);
            if (resp == null || resp.items() == null) return Collections.emptyList();
            List<YouTubeVideo> out = new ArrayList<>(resp.items().size());
            for (PlaylistItem pi : resp.items()) {
                YouTubeVideo v = toVideo(pi, channel);
                if (v != null) out.add(v);
            }
            return out;
        } catch (Exception e) {
            log.warn("YouTube playlistItems.list failed for channel {}: {}",
                channel.channelId(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private static YouTubeVideo toVideo(PlaylistItem pi, ChannelRef channel) {
        if (pi == null || pi.snippet() == null) return null;
        Snippet s = pi.snippet();
        String videoId = pi.contentDetails() == null ? null : pi.contentDetails().videoId();
        if (videoId == null && s.resourceId() != null) videoId = s.resourceId().videoId();
        if (videoId == null) return null;
        OffsetDateTime publishedAt = parseInstant(
            pi.contentDetails() != null && pi.contentDetails().videoPublishedAt() != null
                ? pi.contentDetails().videoPublishedAt()
                : s.publishedAt());
        return new YouTubeVideo(
            videoId,
            channel.channelId(),
            channel.title(),
            channel.handle(),
            s.title(),
            s.description(),
            publishedAt,
            pickThumbnail(s.thumbnails())
        );
    }

    private static OffsetDateTime parseInstant(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return OffsetDateTime.parse(iso);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Prefer medium (320x180) — small enough for a card thumbnail, large enough not
     * to look pixelated. Fall back to default if YouTube didn't generate medium yet
     * (very fresh uploads sometimes have only "default").
     */
    private static String pickThumbnail(Map<String, Thumbnail> thumbnails) {
        if (thumbnails == null) return null;
        for (String key : List.of("medium", "high", "default")) {
            Thumbnail t = thumbnails.get(key);
            if (t != null && t.url() != null && !t.url().isBlank()) return t.url();
        }
        return null;
    }

    public record ChannelRef(String channelId, String uploadsPlaylistId, String title, String handle) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChannelListResponse(List<ChannelItem> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChannelItem(String id, ChannelSnippet snippet, ChannelContentDetails contentDetails) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChannelSnippet(String title) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChannelContentDetails(RelatedPlaylists relatedPlaylists) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RelatedPlaylists(String uploads) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PlaylistItemsResponse(List<PlaylistItem> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PlaylistItem(Snippet snippet, ContentDetails contentDetails) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Snippet(
        String title,
        String description,
        String publishedAt,
        ResourceId resourceId,
        // Linked map preserves insertion order, which Jackson uses for the
        // YouTube "thumbnails" object (default, medium, high, ...).
        @com.fasterxml.jackson.annotation.JsonProperty("thumbnails")
        LinkedHashMap<String, Thumbnail> thumbnails
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResourceId(String videoId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContentDetails(String videoId, String videoPublishedAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Thumbnail(String url) {}
}
