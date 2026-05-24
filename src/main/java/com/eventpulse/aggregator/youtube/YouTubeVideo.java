package com.eventpulse.aggregator.youtube;

import java.time.OffsetDateTime;

/**
 * Normalized view of a YouTube upload as we surface it. Built by {@link YouTubeClient}
 * from the raw playlistItems.list payload — Jackson never sees this record directly.
 *
 * channelHandle is what the topic_aliases table keys on (lowercase, no leading '@').
 * It rides alongside channelId because the alias index is keyed by handle but the
 * URL we render uses channelId.
 */
public record YouTubeVideo(
    String videoId,
    String channelId,
    String channelTitle,
    String channelHandle,
    String title,
    String description,
    OffsetDateTime publishedAt,
    String thumbnailUrl
) {
    public boolean isPresentable() {
        return videoId != null
            && title != null && !title.isBlank()
            && !"Private video".equalsIgnoreCase(title)
            && !"Deleted video".equalsIgnoreCase(title);
    }

    public String watchUrl() {
        return "https://www.youtube.com/watch?v=" + videoId;
    }
}
