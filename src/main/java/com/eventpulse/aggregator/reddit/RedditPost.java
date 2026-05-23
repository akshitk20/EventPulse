package com.eventpulse.aggregator.reddit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Subset of the Reddit listing JSON we care about. Reddit returns plenty more
 * fields (preview images, awards, flair, ...) that we ignore.
 *
 * Field names use Reddit's snake_case so Jackson maps them without configuration.
 * `thumbnail` is sometimes a real URL, sometimes a sentinel like "self"/"default"/
 * "nsfw" — {@link #thumbnailUrl()} only returns it when it looks like a URL.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RedditPost(
    String id,
    String subreddit,
    String title,
    String permalink,
    String url,
    String thumbnail,
    String author,
    Long created_utc,
    Integer score,
    Integer num_comments,
    Boolean stickied,
    Boolean over_18,
    Boolean is_self,
    String post_hint
) {
    public boolean isPresentable(int scoreFloor) {
        return id != null
            && title != null && !title.isBlank()
            && !Boolean.TRUE.equals(stickied)
            && !Boolean.TRUE.equals(over_18)
            && score != null && score >= scoreFloor;
    }

    public String permalinkUrl() {
        if (permalink != null && !permalink.isBlank()) return "https://www.reddit.com" + permalink;
        return url;
    }

    public String externalUrl() {
        if (Boolean.TRUE.equals(is_self)) return permalinkUrl();
        return url != null ? url : permalinkUrl();
    }

    public OffsetDateTime publishedAt() {
        if (created_utc == null) return null;
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(created_utc), ZoneOffset.UTC);
    }

    public String thumbnailUrl() {
        if (thumbnail == null) return null;
        return thumbnail.startsWith("http") ? thumbnail : null;
    }
}
