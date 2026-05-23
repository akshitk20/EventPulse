package com.eventpulse.web;

import com.eventpulse.aggregator.hn.HackerNewsAggregator;
import com.eventpulse.aggregator.reddit.RedditAggregator;
import com.eventpulse.aggregator.sportsdb.SportsDbAggregator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Manual ingest trigger so the dev (and the resume reviewer) can populate the
 * feed without waiting for the scheduler. Permitted only for authenticated
 * users by SecurityConfig — there's no rate limiting yet, so do not expose
 * this without one when going to a real production deploy.
 */
@RestController
@RequestMapping("/admin/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final HackerNewsAggregator hackerNewsAggregator;
    private final SportsDbAggregator sportsDbAggregator;
    private final RedditAggregator redditAggregator;

    @GetMapping("/hn")
    public Map<String, Object> hn() {
        int upserts = hackerNewsAggregator.ingest();
        return Map.of("source", "hn", "upserts", upserts);
    }

    @GetMapping("/sportsdb")
    public Map<String, Object> sportsdb() {
        int upserts = sportsDbAggregator.ingest();
        return Map.of("source", "sportsdb", "upserts", upserts);
    }

    @GetMapping("/reddit")
    public Map<String, Object> reddit() {
        int upserts = redditAggregator.ingest();
        return Map.of("source", "reddit", "upserts", upserts);
    }
}
