package com.eventpulse.aggregator.sportsdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

/**
 * Thin wrapper over TheSportsDB v1 free API. Auth is path-segment only — the
 * key is part of the base URL ("/api/v1/json/3/..."). We deliberately keep
 * this client dumb: it just unwraps the {"events": [...]} envelope and returns
 * a list. Error handling is defensive — any failure logs a warning and yields
 * an empty list so one bad league doesn't poison the whole poll.
 */
@Slf4j
@Component
public class SportsDbClient {

    private final RestClient restClient;

    public SportsDbClient(
        @Value("${eventpulse.sportsdb.base-url:https://www.thesportsdb.com/api/v1/json/3}") String baseUrl
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<SportsDbEvent> nextEventsForLeague(int leagueId) {
        return fetchEvents("/eventsnextleague.php", leagueId);
    }

    public List<SportsDbEvent> pastEventsForLeague(int leagueId) {
        return fetchEvents("/eventspastleague.php", leagueId);
    }

    private List<SportsDbEvent> fetchEvents(String path, int leagueId) {
        try {
            Envelope envelope = restClient.get()
                .uri(uriBuilder -> uriBuilder.path(path).queryParam("id", leagueId).build())
                .retrieve()
                .body(Envelope.class);
            if (envelope == null || envelope.events() == null) return Collections.emptyList();
            return envelope.events();
        } catch (Exception e) {
            log.warn("SportsDB {} fetch failed for league {}: {}", path, leagueId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * The API wraps every list response in {"events": [...]}. When no events
     * match, "events" is JSON null rather than [], so we tolerate both.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Envelope(List<SportsDbEvent> events) {}
}
