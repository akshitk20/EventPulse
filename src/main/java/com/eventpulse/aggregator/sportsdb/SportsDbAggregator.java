package com.eventpulse.aggregator.sportsdb;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Polls TheSportsDB for upcoming and recent events across a small fixed list
 * of leagues, classifies each event against source='sportsdb' aliases, and
 * delegates persistence to the ingest service for transactional safety.
 *
 * Classification is deliberately simple: TheSportsDB events have structured
 * league/sport fields, so we just lowercase those strings (plus a short
 * league key like "epl"/"f1"/"ipl"/"nba") and look them up in the alias
 * index. No substring matching needed — exact equality is enough and keeps
 * the seed migration concise.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SportsDbAggregator {

    /**
     * Hardcoded league list. Free-tier SportsDB IDs are stable, so checking
     * them into source is fine. Add leagues here when expanding the taxonomy.
     * The leagueKey is a short token also used in the V4 alias seed so the
     * aggregator can match without depending on the league's display name.
     */
    private static final List<LeagueConfig> LEAGUES = List.of(
        new LeagueConfig(4328, "epl"),  // English Premier League
        new LeagueConfig(4370, "f1"),   // Formula 1
        new LeagueConfig(4460, "ipl"),  // Indian Premier League (cricket)
        new LeagueConfig(4387, "nba")   // NBA
    );

    private final SportsDbClient client;
    private final SportsDbIngestService ingestService;

    /**
     * Hourly poll, with a 90s initial delay so it doesn't collide with the
     * HN aggregator's boot tick at 60s.
     */
    @Scheduled(initialDelay = 90_000, fixedDelay = 60 * 60_000)
    public void poll() {
        ingest();
    }

    public int ingest() {
        Map<String, List<UUID>> aliasIndex = ingestService.loadAliasIndex();
        if (aliasIndex.isEmpty()) {
            log.info("SportsDB ingest skipped: no topic aliases registered for source={}",
                SportsDbIngestService.SOURCE);
            return 0;
        }

        int upserts = 0;
        for (LeagueConfig league : LEAGUES) {
            upserts += pollLeague(league, aliasIndex);
        }
        log.info("SportsDB ingest complete: upserted {}", upserts);
        return upserts;
    }

    private int pollLeague(LeagueConfig league, Map<String, List<UUID>> aliasIndex) {
        int upserts = 0;
        List<SportsDbEvent> events = client.nextEventsForLeague(league.leagueId());
        upserts += ingestAll(events, league, aliasIndex);
        events = client.pastEventsForLeague(league.leagueId());
        upserts += ingestAll(events, league, aliasIndex);
        return upserts;
    }

    private int ingestAll(List<SportsDbEvent> events, LeagueConfig league,
                          Map<String, List<UUID>> aliasIndex) {
        int upserts = 0;
        for (SportsDbEvent ev : events) {
            if (!ev.isPresentable()) continue;
            Set<UUID> matched = classify(ev, league, aliasIndex);
            if (matched.isEmpty()) continue;
            try {
                ingestService.ingestOne(ev, matched);
                upserts++;
            } catch (Exception e) {
                log.warn("SportsDB ingestOne failed for event {}: {}", ev.idEvent(), e.getMessage());
            }
        }
        return upserts;
    }

    /**
     * Look the event up against three keys: the lowercased league name, the
     * lowercased sport name, and the configured short league key. Any alias
     * hit contributes its mapped interest IDs.
     */
    private Set<UUID> classify(SportsDbEvent ev, LeagueConfig league,
                               Map<String, List<UUID>> aliasIndex) {
        Set<UUID> hits = new HashSet<>();
        addHits(hits, aliasIndex, league.leagueKey());
        if (ev.strLeague() != null) addHits(hits, aliasIndex, ev.strLeague().toLowerCase(Locale.ROOT));
        if (ev.strSport() != null)  addHits(hits, aliasIndex, ev.strSport().toLowerCase(Locale.ROOT));
        return hits;
    }

    private static void addHits(Set<UUID> hits, Map<String, List<UUID>> aliasIndex, String key) {
        List<UUID> ids = aliasIndex.get(key);
        if (ids != null) hits.addAll(ids);
    }

    private record LeagueConfig(int leagueId, String leagueKey) {}
}
