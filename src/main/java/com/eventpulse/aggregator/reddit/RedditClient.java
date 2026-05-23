package com.eventpulse.aggregator.reddit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thin wrapper over Reddit's public listing JSON. No auth and no API key —
 * Reddit serves /r/{sub}/hot.json straight to anyone with a sane User-Agent.
 *
 * Reddit blocks default Java/Apache user agents with a 429, so we set a
 * descriptive UA on every request. raw_json=1 disables HTML-encoding of
 * special characters in titles, which saves us from decoding back.
 */
@Slf4j
@Component
public class RedditClient {

    private final RestClient restClient;

    public RedditClient(
        @Value("${eventpulse.reddit.base-url:https://www.reddit.com}") String baseUrl,
        @Value("${eventpulse.reddit.user-agent:eventpulse/0.1 (by /u/eventpulse)}") String userAgent
    ) {
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("User-Agent", userAgent)
            .build();
    }

    public List<RedditPost> hotForSubreddit(String subreddit, int limit) {
        try {
            Listing listing = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/r/{sub}/hot.json")
                    .queryParam("limit", limit)
                    .queryParam("raw_json", 1)
                    .build(subreddit))
                .retrieve()
                .body(Listing.class);
            if (listing == null || listing.data() == null || listing.data().children() == null) {
                return Collections.emptyList();
            }
            List<RedditPost> posts = new ArrayList<>(listing.data().children().size());
            for (Child c : listing.data().children()) {
                if (c != null && c.data() != null) posts.add(c.data());
            }
            return posts;
        } catch (Exception e) {
            log.warn("Reddit hot fetch failed for r/{}: {}", subreddit, e.getMessage());
            return Collections.emptyList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Listing(ListingData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ListingData(List<Child> children) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Child(RedditPost data) {}
}
