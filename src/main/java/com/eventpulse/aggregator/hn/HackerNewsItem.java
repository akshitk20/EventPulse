package com.eventpulse.aggregator.hn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subset of the HN item JSON we care about. HN returns plenty more fields
 * (kids, parts, parent, descendants, ...) that we ignore.
 *
 * `type` is one of: story, comment, job, poll, pollopt. We only keep stories.
 * `url` is null for self-posts; we fall back to the HN item permalink in that case.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HackerNewsItem(
    Long id,
    String type,
    String title,
    String url,
    String by,
    Long time,
    Boolean dead,
    Boolean deleted
) {
    public boolean isLiveStory() {
        return "story".equals(type)
            && !Boolean.TRUE.equals(dead)
            && !Boolean.TRUE.equals(deleted)
            && title != null && !title.isBlank();
    }

    public String permalinkUrl() {
        return url != null ? url : "https://news.ycombinator.com/item?id=" + id;
    }
}
