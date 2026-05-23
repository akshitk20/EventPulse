package com.eventpulse.aggregator.hn;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

/**
 * Thin wrapper over the public Hacker News Firebase API. No auth, no API key.
 *   - top stories list:  /v0/topstories.json
 *   - story detail:      /v0/item/{id}.json
 *
 * We deliberately keep this client dumb: no classification, no persistence.
 * The aggregator decides which items become FeedItems.
 */
@Slf4j
@Component
public class HackerNewsClient {

    private final RestClient restClient;

    public HackerNewsClient(@Value("${eventpulse.hn.base-url:https://hacker-news.firebaseio.com}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<Long> topStoryIds() {
        try {
            Long[] ids = restClient.get().uri("/v0/topstories.json").retrieve().body(Long[].class);
            return ids == null ? Collections.emptyList() : List.of(ids);
        } catch (Exception e) {
            log.warn("HN topstories fetch failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public HackerNewsItem item(long id) {
        try {
            return restClient.get().uri("/v0/item/{id}.json", id).retrieve().body(HackerNewsItem.class);
        } catch (Exception e) {
            log.warn("HN item {} fetch failed: {}", id, e.getMessage());
            return null;
        }
    }
}
